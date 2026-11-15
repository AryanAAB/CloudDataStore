package org.cloudstore.controller.proxy;

import org.cloudstore.common.util.ChunkHashUtils;
import org.cloudstore.controller.master.PlacementEngine;
import org.cloudstore.controller.master.StorageAccountingService;
import org.cloudstore.controller.master.WorkerRegistryService;
import org.cloudstore.controller.model.ChunkRecord;
import org.cloudstore.controller.model.ChunkReplica;
import org.cloudstore.controller.model.WorkerNode;
import org.cloudstore.controller.repository.ChunkRecordRepository;
import org.cloudstore.controller.repository.ChunkReplicaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Headless replication proxy spawned by the Master to heal under-replicated
 * or corrupted chunks by copying verified data to a healthy worker.
 */
@Service
public class HealProxy
{
    private static final Logger LOGGER = LoggerFactory.getLogger(HealProxy.class);

    private final WorkerRegistryService workerRegistry;
    private final StorageAccountingService storageAccounting;
    private final PlacementEngine placementEngine;
    private final WorkerChunkClient workerChunkClient;
    private final ChunkRecordRepository chunkRecordRepository;
    private final ChunkReplicaRepository chunkReplicaRepository;

    public HealProxy(
            WorkerRegistryService workerRegistry,
            StorageAccountingService storageAccounting,
            PlacementEngine placementEngine,
            WorkerChunkClient workerChunkClient,
            ChunkRecordRepository chunkRecordRepository,
            ChunkReplicaRepository chunkReplicaRepository)
    {
        this.workerRegistry = workerRegistry;
        this.storageAccounting = storageAccounting;
        this.placementEngine = placementEngine;
        this.workerChunkClient = workerChunkClient;
        this.chunkRecordRepository = chunkRecordRepository;
        this.chunkReplicaRepository = chunkReplicaRepository;
    }

    /**
     * Attempts to heal a single chunk by reading from a live healthy replica
     * and writing to a replacement worker.
     * Returns true if healing succeeded, false otherwise.
     */
    public boolean repairChunk(String chunkId)
    {
        Optional<ChunkRecord> chunkOpt = chunkRecordRepository.findById(chunkId);
        if (chunkOpt.isEmpty())
        {
            LOGGER.warn("Cannot heal chunk {}: record not found in database", chunkId);
            return false;
        }

        ChunkRecord chunk = chunkOpt.get();
        List<ChunkReplica> existingReplicas = chunkReplicaRepository.findByChunkId(chunkId);

        // 1. Find a live, healthy source replica
        byte[] validChunkBytes = null;
        for (ChunkReplica replica : existingReplicas)
        {
            if (!replica.isHealthy())
            {
                continue;
            }

            Optional<WorkerNode> workerOpt = workerRegistry.getWorker(replica.getWorkerId());
            if (workerOpt.isEmpty() || !workerOpt.get().isUp())
            {
                continue;
            }

            WorkerNode sourceWorker = workerOpt.get();
            String reservationKey = UUID.randomUUID().toString();
            byte[] bytes = workerChunkClient.downloadChunk(
                    sourceWorker.getAddress(), chunk.getObjectId(), chunk.getChunkId(), reservationKey);

            if (bytes != null)
            {
                String actualHash = ChunkHashUtils.sha256Hex(bytes);
                if (actualHash.equalsIgnoreCase(chunk.getSha256Hash()))
                {
                    validChunkBytes = bytes;
                    LOGGER.info("Read verified source copy of chunk {} from worker {}",
                            chunkId, sourceWorker.getWorkerId());
                    break;
                }
                else
                {
                    LOGGER.warn("Source worker {} returned corrupted data for chunk {}",
                            sourceWorker.getWorkerId(), chunkId);
                    markReplicaCorrupt(replica);
                }
            }
        }

        if (validChunkBytes == null)
        {
            LOGGER.error("Cannot heal chunk {}: no live healthy replica available to read from!", chunkId);
            return false;
        }

        // 2. Identify workers already hosting a healthy copy so we don't duplicate on them
        Set<String> excludedWorkerIds = new HashSet<>();
        for (ChunkReplica r : existingReplicas)
        {
            Optional<WorkerNode> w = workerRegistry.getWorker(r.getWorkerId());
            if (w.isPresent() && w.get().isUp() && r.isHealthy())
            {
                excludedWorkerIds.add(r.getWorkerId());
            }
        }

        // 3. Select a healthy replacement worker with capacity
        Optional<WorkerNode> targetWorkerOpt = placementEngine.selectReplacementWorker(
                validChunkBytes.length, excludedWorkerIds);

        if (targetWorkerOpt.isEmpty())
        {
            LOGGER.warn("Cannot heal chunk {}: no eligible target worker available with required storage", chunkId);
            return false;
        }

        WorkerNode targetWorker = targetWorkerOpt.get();
        String targetWorkerId = targetWorker.getWorkerId();

        // 4. Reserve capacity on target worker
        if (!storageAccounting.reserveCapacity(targetWorkerId, validChunkBytes.length))
        {
            LOGGER.warn("Failed to reserve storage on target worker {} for chunk repair {}",
                    targetWorkerId, chunkId);
            return false;
        }

        // 5. Reserve chunk and upload to target worker
        String reservationKey = UUID.randomUUID().toString();
        boolean reserved = workerChunkClient.reserveChunk(
                targetWorker.getAddress(), chunk.getObjectId(), chunk.getChunkId(), reservationKey);

        if (!reserved)
        {
            LOGGER.warn("Target worker {} rejected reservation for chunk {}", targetWorkerId, chunkId);
            storageAccounting.releaseCapacity(targetWorkerId, validChunkBytes.length);
            return false;
        }

        boolean uploaded = workerChunkClient.uploadChunk(
                targetWorker.getAddress(),
                chunk.getObjectId(),
                chunk.getChunkId(),
                reservationKey,
                validChunkBytes,
                chunk.getSha256Hash());

        if (!uploaded)
        {
            LOGGER.error("Failed to upload healed chunk {} to target worker {}", chunkId, targetWorkerId);
            storageAccounting.releaseCapacity(targetWorkerId, validChunkBytes.length);
            return false;
        }

        // 6. Record new replica in database
        saveHealedReplica(chunkId, targetWorkerId);
        LOGGER.info("SUCCESS: Chunk {} healed and re-replicated to worker {}", chunkId, targetWorkerId);
        return true;
    }

    @Transactional
    protected void markReplicaCorrupt(ChunkReplica replica)
    {
        replica.setReplicaState(ChunkReplica.STATE_CORRUPT);
        chunkReplicaRepository.save(replica);
    }

    @Transactional
    protected void saveHealedReplica(String chunkId, String workerId)
    {
        Optional<ChunkReplica> existing = chunkReplicaRepository.findByChunkIdAndWorkerId(chunkId, workerId);
        if (existing.isPresent())
        {
            ChunkReplica r = existing.get();
            r.setReplicaState(ChunkReplica.STATE_OK);
            r.setLastVerifiedAt(java.time.Instant.now());
            chunkReplicaRepository.save(r);
        }
        else
        {
            ChunkReplica r = new ChunkReplica(chunkId, workerId);
            chunkReplicaRepository.save(r);
        }
    }
}

