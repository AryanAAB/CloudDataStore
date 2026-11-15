package org.cloudstore.controller.proxy;

import org.cloudstore.common.util.ChunkHashUtils;
import org.cloudstore.controller.dto.SessionInitiateRequest;
import org.cloudstore.controller.dto.SessionInitiateResponse;
import org.cloudstore.controller.dto.SessionStatusResponse;
import org.cloudstore.controller.exception.QuotaExceededException;
import org.cloudstore.controller.exception.StorageUnavailableException;
import org.cloudstore.controller.master.PlacementEngine;
import org.cloudstore.controller.master.StorageAccountingService;
import org.cloudstore.controller.master.WorkerRegistryService;
import org.cloudstore.controller.model.*;
import org.cloudstore.controller.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * High-performance virtual thread-backed proxy orchestrating upload chunking,
 * dual-write replication, mid-stream failover, and verified streaming downloads.
 */
@Service
public class UProxyService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(UProxyService.class);

    private final WorkerRegistryService workerRegistry;
    private final StorageAccountingService storageAccounting;
    private final PlacementEngine placementEngine;
    private final WorkerChunkClient workerChunkClient;

    private final StoredObjectRepository storedObjectRepository;
    private final ChunkRecordRepository chunkRecordRepository;
    private final ChunkReplicaRepository chunkReplicaRepository;
    private final UploadSessionRepository uploadSessionRepository;

    private final int chunkSize;
    private final Duration sessionTtl;
    private final ExecutorService virtualExecutor;

    public UProxyService(
            WorkerRegistryService workerRegistry,
            StorageAccountingService storageAccounting,
            PlacementEngine placementEngine,
            WorkerChunkClient workerChunkClient,
            StoredObjectRepository storedObjectRepository,
            ChunkRecordRepository chunkRecordRepository,
            ChunkReplicaRepository chunkReplicaRepository,
            UploadSessionRepository uploadSessionRepository,
            @Value("${controller.session.chunk-size-bytes:1048576}") int chunkSize,
            @Value("${controller.session.ttl-seconds:600}") long sessionTtlSeconds)
    {
        this.workerRegistry = workerRegistry;
        this.storageAccounting = storageAccounting;
        this.placementEngine = placementEngine;
        this.workerChunkClient = workerChunkClient;
        this.storedObjectRepository = storedObjectRepository;
        this.chunkRecordRepository = chunkRecordRepository;
        this.chunkReplicaRepository = chunkReplicaRepository;
        this.uploadSessionRepository = uploadSessionRepository;
        this.chunkSize = chunkSize;
        this.sessionTtl = Duration.ofSeconds(sessionTtlSeconds);
        this.virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Step 1: Pre-flight validation, cluster storage verification, and session reservation.
     */
    @Transactional
    public SessionInitiateResponse initiateSession(SessionInitiateRequest request)
    {
        int replicationFactor = placementEngine.getReplicationFactor();
        List<WorkerNode> activeWorkers = workerRegistry.getActiveWorkers();
        if (activeWorkers.size() < replicationFactor)
        {
            throw new StorageUnavailableException(String.format(
                    "Insufficient active workers: required %d, currently active %d",
                    replicationFactor, activeWorkers.size()));
        }

        long declaredSize = Math.max(0, request.size());
        int totalChunks = declaredSize == 0 ? 1 : (int) Math.ceil((double) declaredSize / chunkSize);

        long totalClusterBytesNeeded = (long) totalChunks * chunkSize * replicationFactor;
        if (storageAccounting.getTotalAvailableCapacity() < totalClusterBytesNeeded)
        {
            throw new StorageUnavailableException(String.format(
                    "Cluster storage exhausted: needed %d bytes, available %d bytes",
                    totalClusterBytesNeeded, storageAccounting.getTotalAvailableCapacity()));
        }

        String objectId = "obj-" + UUID.randomUUID().toString().substring(0, 12);
        String sessionId = "sess-" + UUID.randomUUID().toString().substring(0, 12);
        String sessionKey = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(sessionTtl);

        StoredObject storedObject = new StoredObject(
                objectId,
                request.name() != null ? request.name() : "unnamed_object",
                declaredSize,
                request.contentType() != null ? request.contentType() : "application/octet-stream",
                totalChunks);
        storedObject.setCustomMetadata(request.metadata());
        storedObjectRepository.save(storedObject);

        UploadSession session = new UploadSession(
                sessionId,
                sessionKey,
                objectId,
                storedObject.getName(),
                declaredSize,
                storedObject.getContentType(),
                totalChunks,
                expiresAt,
                request.metadata());
        uploadSessionRepository.save(session);

        LOGGER.info("Initiated session {} for object {} (size: {} bytes, chunks: {})",
                sessionId, objectId, declaredSize, totalChunks);

        String uploadUrl = "/api/v1/objects/sessions/" + sessionId + "/data";
        return new SessionInitiateResponse(
                objectId,
                sessionId,
                sessionKey,
                chunkSize,
                totalChunks,
                uploadUrl,
                expiresAt);
    }

    /**
     * Step 2: Streams client binary data into chunks, executing dual-write to workers.
     */
    public StoredObject processStreamUpload(String sessionId, String sessionKey, InputStream inputStream)
            throws IOException
    {
        UploadSession session = uploadSessionRepository.findBySessionIdAndSessionKey(sessionId, sessionKey)
                .orElseThrow(() -> new IllegalArgumentException("Invalid session ID or unauthorized session key"));

        if (session.isExpired())
        {
            session.setStatus(UploadSession.STATUS_EXPIRED);
            uploadSessionRepository.save(session);
            throw new IllegalStateException("Upload session has expired");
        }

        session.setStatus(UploadSession.STATUS_STREAMING);
        uploadSessionRepository.save(session);

        ChunkStreamPipeline pipeline = new ChunkStreamPipeline(
                inputStream,
                session.getDeclaredSize(),
                session.getTotalChunks(),
                chunkSize);

        String objectId = session.getObjectId();
        int replicationFactor = placementEngine.getReplicationFactor();

        ChunkStreamPipeline.ChunkPayload chunk;
        while ((chunk = pipeline.readNextChunk()) != null)
        {
            final String chunkId = objectId + "_chunk_" + chunk.chunkIndex();
            final byte[] chunkBytes = chunk.data();
            final String sha256Hex = chunk.sha256Hex();

            // 1. Placement: select N distinct UP workers
            List<WorkerNode> chosenWorkers = placementEngine.selectWorkersForChunk(
                    replicationFactor, chunkBytes.length, Collections.emptySet());

            // 2. Reserve and upload in parallel to all chosen workers
            Set<String> successfulWorkerIds = uploadToWorkersWithFailover(
                    objectId, chunkId, chunkBytes, sha256Hex, chosenWorkers, replicationFactor);

            // 3. Persist chunk record and replicas
            saveChunkMetadata(chunkId, objectId, chunk.chunkIndex(), chunkBytes.length, sha256Hex, successfulWorkerIds);

            // 4. Update session progress
            session.setNextExpectedChunkIndex(chunk.chunkIndex() + 1);
            uploadSessionRepository.save(session);
        }

        // Commit object
        StoredObject storedObject = storedObjectRepository.findById(objectId)
                .orElseThrow(() -> new IllegalStateException("Object record not found: " + objectId));

        storedObject.setSize(pipeline.getTotalBytesRead());
        storedObject.setChecksum(pipeline.getFinalFileChecksum());
        storedObject.setStatus(StoredObject.STATUS_COMMITTED);
        storedObject.setUpdatedAt(Instant.now());
        storedObjectRepository.save(storedObject);

        session.setStatus(UploadSession.STATUS_COMMITTED);
        uploadSessionRepository.save(session);

        LOGGER.info("Successfully committed object {} (total size: {} bytes, checksum: {})",
                objectId, storedObject.getSize(), storedObject.getChecksum());

        return storedObject;
    }

    /**
     * Parallel dual-write with dynamic mid-upload failover to replacement workers.
     */
    private Set<String> uploadToWorkersWithFailover(
            String objectId,
            String chunkId,
            byte[] chunkBytes,
            String sha256Hex,
            List<WorkerNode> initialWorkers,
            int requiredReplicas)
    {
        Set<String> successfulWorkerIds = ConcurrentHashMap.newKeySet();
        Set<String> attemptedWorkerIds = ConcurrentHashMap.newKeySet();

        List<CompletableFuture<Void>> uploadFutures = new ArrayList<>();
        for (WorkerNode worker : initialWorkers)
        {
            attemptedWorkerIds.add(worker.getWorkerId());
            uploadFutures.add(CompletableFuture.runAsync(
                    () -> writeChunkToWorker(objectId, chunkId, chunkBytes, sha256Hex, worker, successfulWorkerIds),
                    virtualExecutor));
        }

        CompletableFuture.allOf(uploadFutures.toArray(new CompletableFuture[0])).join();

        // Failover: if any worker failed, pick replacements until requiredReplicas is satisfied
        while (successfulWorkerIds.size() < requiredReplicas)
        {
            Optional<WorkerNode> replacementOpt = placementEngine.selectReplacementWorker(
                    chunkBytes.length, attemptedWorkerIds);

            if (replacementOpt.isEmpty())
            {
                throw new StorageUnavailableException(String.format(
                        "Mid-stream failover failed for chunk %s: insufficient replacement workers available", chunkId));
            }

            WorkerNode replacement = replacementOpt.get();
            attemptedWorkerIds.add(replacement.getWorkerId());

            LOGGER.warn("Worker failure during write of chunk {}. Attempting failover to replacement worker {}",
                    chunkId, replacement.getWorkerId());

            writeChunkToWorker(objectId, chunkId, chunkBytes, sha256Hex, replacement, successfulWorkerIds);
        }

        return successfulWorkerIds;
    }

    private void writeChunkToWorker(
            String objectId,
            String chunkId,
            byte[] chunkBytes,
            String sha256Hex,
            WorkerNode worker,
            Set<String> successfulWorkerIds)
    {
        String workerId = worker.getWorkerId();
        if (!storageAccounting.reserveCapacity(workerId, chunkBytes.length))
        {
            LOGGER.warn("Failed to reserve storage on worker {} for chunk {}", workerId, chunkId);
            return;
        }

        String reservationKey = UUID.randomUUID().toString();
        boolean reserved = workerChunkClient.reserveChunk(
                worker.getAddress(), objectId, chunkId, reservationKey);

        if (!reserved)
        {
            LOGGER.warn("Worker {} rejected chunk reservation for {}", workerId, chunkId);
            storageAccounting.releaseCapacity(workerId, chunkBytes.length);
            return;
        }

        boolean uploaded = workerChunkClient.uploadChunk(
                worker.getAddress(), objectId, chunkId, reservationKey, chunkBytes, sha256Hex);

        if (uploaded)
        {
            successfulWorkerIds.add(workerId);
            LOGGER.debug("Chunk {} successfully written and verified on worker {}", chunkId, workerId);
        }
        else
        {
            LOGGER.warn("Worker {} upload failed for chunk {}", workerId, chunkId);
            storageAccounting.releaseCapacity(workerId, chunkBytes.length);
        }
    }

    @Transactional
    protected void saveChunkMetadata(
            String chunkId,
            String objectId,
            int chunkIndex,
            long chunkSize,
            String sha256Hex,
            Set<String> workerIds)
    {
        ChunkRecord record = new ChunkRecord(chunkId, objectId, chunkIndex, chunkSize, sha256Hex);
        chunkRecordRepository.save(record);

        for (String workerId : workerIds)
        {
            ChunkReplica replica = new ChunkReplica(chunkId, workerId);
            chunkReplicaRepository.save(replica);
        }
    }

    /**
     * Streams complete verified object to the given output stream with replica fallback.
     */
    public void streamDownload(String objectId, OutputStream outputStream) throws IOException
    {
        StoredObject storedObject = storedObjectRepository.findById(objectId)
                .orElseThrow(() -> new IllegalArgumentException("Object not found: " + objectId));

        if (!StoredObject.STATUS_COMMITTED.equalsIgnoreCase(storedObject.getStatus()))
        {
            throw new IllegalStateException("Object is not committed: " + objectId);
        }

        List<ChunkRecord> chunks = chunkRecordRepository.findByObjectIdOrderByChunkIndexAsc(objectId);
        for (ChunkRecord chunk : chunks)
        {
            byte[] chunkData = downloadChunkWithFallback(chunk);
            outputStream.write(chunkData);
        }
        outputStream.flush();
    }

    /**
     * Reads a chunk from primary replica; on failure/corruption, falls back to replica 2.
     */
    private byte[] downloadChunkWithFallback(ChunkRecord chunk)
    {
        List<ChunkReplica> replicas = chunkReplicaRepository.findByChunkId(chunk.getChunkId());
        if (replicas.isEmpty())
        {
            throw new StorageUnavailableException("No replicas recorded for chunk: " + chunk.getChunkId());
        }

        for (ChunkReplica replica : replicas)
        {
            Optional<WorkerNode> workerOpt = workerRegistry.getWorker(replica.getWorkerId());
            if (workerOpt.isEmpty() || !workerOpt.get().isUp())
            {
                LOGGER.warn("Worker {} for chunk {} is DOWN. Trying next replica.",
                        replica.getWorkerId(), chunk.getChunkId());
                continue;
            }

            WorkerNode worker = workerOpt.get();
            String reservationKey = UUID.randomUUID().toString();
            byte[] bytes = workerChunkClient.downloadChunk(
                    worker.getAddress(), chunk.getObjectId(), chunk.getChunkId(), reservationKey);

            if (bytes == null)
            {
                LOGGER.warn("Failed to download chunk {} from worker {}. Trying next replica.",
                        chunk.getChunkId(), worker.getWorkerId());
                continue;
            }

            // Verify integrity
            String actualSha256 = ChunkHashUtils.sha256Hex(bytes);
            if (!actualSha256.equalsIgnoreCase(chunk.getSha256Hash()))
            {
                LOGGER.error("CORRUPTION DETECTED: Chunk {} on worker {} failed SHA-256 (expected {}, got {})",
                        chunk.getChunkId(), worker.getWorkerId(), chunk.getSha256Hash(), actualSha256);
                replica.setReplicaState(ChunkReplica.STATE_CORRUPT);
                chunkReplicaRepository.save(replica);
                continue;
            }

            // Successfully retrieved and verified
            return bytes;
        }

        throw new StorageUnavailableException("Unrecoverable data loss: all replicas for chunk "
                + chunk.getChunkId() + " failed or are corrupted");
    }

    public SessionStatusResponse getSessionStatus(String sessionId)
    {
        UploadSession session = uploadSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        return new SessionStatusResponse(
                session.getSessionId(),
                session.getObjectId(),
                session.getStatus(),
                session.getTotalChunks(),
                session.getNextExpectedChunkIndex(),
                session.getExpiresAt());
    }

    public Optional<StoredObject> getObject(String objectId)
    {
        return storedObjectRepository.findById(objectId);
    }

    public List<StoredObject> listCommittedObjects()
    {
        return storedObjectRepository.findByStatus(StoredObject.STATUS_COMMITTED);
    }
}

