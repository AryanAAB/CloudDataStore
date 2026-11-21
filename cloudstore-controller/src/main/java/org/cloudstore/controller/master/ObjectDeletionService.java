package org.cloudstore.controller.master;

import org.cloudstore.controller.model.*;
import org.cloudstore.controller.proxy.WorkerChunkClient;
import org.cloudstore.controller.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles two-phase distributed deletion: immediate quota release & soft-delete,
 * followed by asynchronous chunk purging and tombstone recording for offline workers.
 */
@Service
public class ObjectDeletionService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ObjectDeletionService.class);

    private final StoredObjectRepository storedObjectRepository;
    private final ChunkRecordRepository chunkRecordRepository;
    private final ChunkReplicaRepository chunkReplicaRepository;
    private final PendingDeletionRepository pendingDeletionRepository;
    private final WorkerRegistryService workerRegistry;
    private final StorageAccountingService storageAccounting;
    private final WorkerChunkClient workerChunkClient;

    private final ExecutorService virtualExecutor;

    public ObjectDeletionService(
            StoredObjectRepository storedObjectRepository,
            ChunkRecordRepository chunkRecordRepository,
            ChunkReplicaRepository chunkReplicaRepository,
            PendingDeletionRepository pendingDeletionRepository,
            WorkerRegistryService workerRegistry,
            StorageAccountingService storageAccounting,
            WorkerChunkClient workerChunkClient)
    {
        this.storedObjectRepository = storedObjectRepository;
        this.chunkRecordRepository = chunkRecordRepository;
        this.chunkReplicaRepository = chunkReplicaRepository;
        this.pendingDeletionRepository = pendingDeletionRepository;
        this.workerRegistry = workerRegistry;
        this.storageAccounting = storageAccounting;
        this.workerChunkClient = workerChunkClient;
        this.virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Deletes an object: immediately reclaims cluster storage and schedules worker chunk purges.
     * Returns true if deleted, false if object not found or already deleted.
     */
    @Transactional
    public boolean deleteObject(String objectId)
    {
        Optional<StoredObject> opt = storedObjectRepository.findById(objectId);
        if (opt.isEmpty() || StoredObject.STATUS_DELETED.equalsIgnoreCase(opt.get().getStatus()))
        {
            return false;
        }

        StoredObject object = opt.get();
        object.setStatus(StoredObject.STATUS_DELETED);
        object.setUpdatedAt(Instant.now());
        storedObjectRepository.save(object);

        List<ChunkRecord> chunks = chunkRecordRepository.findByObjectIdOrderByChunkIndexAsc(objectId);

        for (ChunkRecord chunk : chunks)
        {
            List<ChunkReplica> replicas = chunkReplicaRepository.findByChunkId(chunk.getChunkId());
            for (ChunkReplica replica : replicas)
            {
                String workerId = replica.getWorkerId();
                // 1. Immediately refund storage quota back to worker
                storageAccounting.releaseCapacity(workerId, chunk.getChunkSize());

                // 2. Dispatch worker chunk deletion or record tombstone
                dispatchWorkerChunkDeletion(workerId, objectId, chunk.getChunkId());
            }

            // Clean up chunk metadata records
            chunkReplicaRepository.deleteByChunkId(chunk.getChunkId());
        }

        chunkRecordRepository.deleteByObjectId(objectId);
        LOGGER.info("Successfully deleted object {} and refunded storage for {} chunks",
                objectId, chunks.size());

        return true;
    }

    private void dispatchWorkerChunkDeletion(String workerId, String objectId, String chunkId)
    {
        Optional<WorkerNode> workerOpt = workerRegistry.getWorker(workerId);
        if (workerOpt.isEmpty() || !workerOpt.get().isUp())
        {
            LOGGER.warn("Worker {} is currently DOWN. Storing pending deletion tombstone for chunk {}",
                    workerId, chunkId);
            pendingDeletionRepository.save(new PendingDeletion(workerId, objectId, chunkId));
            return;
        }

        WorkerNode worker = workerOpt.get();
        virtualExecutor.submit(() -> {
            String reservationKey = UUID.randomUUID().toString();
            boolean deleted = workerChunkClient.deleteChunk(
                    worker.getAddress(), objectId, chunkId, reservationKey);

            if (!deleted)
            {
                LOGGER.warn("Failed to delete chunk {} on worker {}. Recording pending tombstone.",
                        chunkId, workerId);
                pendingDeletionRepository.save(new PendingDeletion(workerId, objectId, chunkId));
            }
            else
            {
                LOGGER.debug("Purged chunk {} on worker {}", chunkId, workerId);
            }
        });
    }

    /**
     * Purges pending chunk tombstones when an offline worker reconnects.
     */
    @org.springframework.context.event.EventListener
    public void onWorkerReconnected(org.cloudstore.controller.master.event.WorkerReconnectedEvent event)
    {
        reconcilePendingDeletions(event.workerId());
    }

    public void reconcilePendingDeletions(String workerId)
    {
        List<PendingDeletion> pending = pendingDeletionRepository.findByWorkerId(workerId);
        if (pending.isEmpty())
        {
            return;
        }

        Optional<WorkerNode> workerOpt = workerRegistry.getWorker(workerId);
        if (workerOpt.isEmpty() || !workerOpt.get().isUp())
        {
            return;
        }

        WorkerNode worker = workerOpt.get();
        LOGGER.info("Worker {} reconnected. Purging {} pending chunk tombstones...",
                workerId, pending.size());

        for (PendingDeletion p : pending)
        {
            virtualExecutor.submit(() -> {
                String reservationKey = UUID.randomUUID().toString();
                boolean deleted = workerChunkClient.deleteChunk(
                        worker.getAddress(), p.getObjectId(), p.getChunkId(), reservationKey);
                if (deleted)
                {
                    pendingDeletionRepository.delete(p);
                    LOGGER.debug("Reconciled tombstone for chunk {} on worker {}", p.getChunkId(), workerId);
                }
            });
        }
    }
}
