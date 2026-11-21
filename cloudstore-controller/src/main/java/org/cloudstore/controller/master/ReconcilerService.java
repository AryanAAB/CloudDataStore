package org.cloudstore.controller.master;

import org.cloudstore.controller.model.ChunkRecord;
import org.cloudstore.controller.model.ChunkReplica;
import org.cloudstore.controller.model.StoredObject;
import org.cloudstore.controller.model.WorkerNode;
import org.cloudstore.controller.proxy.HealProxy;
import org.cloudstore.controller.repository.ChunkRecordRepository;
import org.cloudstore.controller.repository.ChunkReplicaRepository;
import org.cloudstore.controller.repository.StoredObjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Background reconciler loop that periodically scans committed objects,
 * discovers under-replicated chunks, and spawns HealProxy workers to restore
 * the required replication factor.
 */
@Service
public class ReconcilerService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ReconcilerService.class);

    private final StoredObjectRepository storedObjectRepository;
    private final ChunkRecordRepository chunkRecordRepository;
    private final ChunkReplicaRepository chunkReplicaRepository;
    private final WorkerRegistryService workerRegistry;
    private final HealProxy healProxy;
    private final PlacementEngine placementEngine;

    private final Set<String> activeHealingChunks = ConcurrentHashMap.newKeySet();
    private final ExecutorService virtualExecutor;

    public ReconcilerService(
            StoredObjectRepository storedObjectRepository,
            ChunkRecordRepository chunkRecordRepository,
            ChunkReplicaRepository chunkReplicaRepository,
            WorkerRegistryService workerRegistry,
            HealProxy healProxy,
            PlacementEngine placementEngine)
    {
        this.storedObjectRepository = storedObjectRepository;
        this.chunkRecordRepository = chunkRecordRepository;
        this.chunkReplicaRepository = chunkReplicaRepository;
        this.workerRegistry = workerRegistry;
        this.healProxy = healProxy;
        this.placementEngine = placementEngine;
        this.virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Periodic watchdog detecting under-replicated chunks across the cluster.
     */
    @Scheduled(fixedDelayString = "${controller.reconciler.interval-ms:5000}")
    public void runReconciliation()
    {
        int requiredReplicas = placementEngine.getReplicationFactor();
        List<StoredObject> committedObjects =
                storedObjectRepository.findByStatus(StoredObject.STATUS_COMMITTED);

        for (StoredObject object : committedObjects)
        {
            List<ChunkRecord> chunks = chunkRecordRepository.findByObjectIdOrderByChunkIndexAsc(object.getObjectId());
            for (ChunkRecord chunk : chunks)
            {
                checkAndHealChunk(chunk, requiredReplicas);
            }
        }
    }

    private void checkAndHealChunk(ChunkRecord chunk, int requiredReplicas)
    {
        String chunkId = chunk.getChunkId();
        if (activeHealingChunks.contains(chunkId))
        {
            return;
        }

        List<ChunkReplica> replicas = chunkReplicaRepository.findByChunkId(chunkId);
        long liveHealthyCopies = replicas.stream()
                .filter(ChunkReplica::isHealthy)
                .filter(r -> {
                    Optional<WorkerNode> w = workerRegistry.getWorker(r.getWorkerId());
                    return w.isPresent() && w.get().isUp();
                })
                .count();

        if (liveHealthyCopies < requiredReplicas)
        {
            LOGGER.warn("Under-replication detected for chunk {}: {} live healthy copies (required: {})",
                    chunkId, liveHealthyCopies, requiredReplicas);

            if (liveHealthyCopies == 0)
            {
                LOGGER.error("CRITICAL: Chunk {} has 0 live healthy copies! Waiting for workers to recover.", chunkId);
                return;
            }

            activeHealingChunks.add(chunkId);
            virtualExecutor.submit(() -> {
                try
                {
                    LOGGER.info("Spawning HealProxy for chunk {}", chunkId);
                    healProxy.repairChunk(chunkId);
                }
                catch (Exception e)
                {
                    LOGGER.error("HealProxy execution failed for chunk {}", chunkId, e);
                }
                finally
                {
                    activeHealingChunks.remove(chunkId);
                }
            });
        }
    }

    /**
     * Triggers an immediate reconciliation cycle on demand.
     */
    public void triggerReconciliation()
    {
        runReconciliation();
    }

    public int getActiveHealingTasksCount()
    {
        return activeHealingChunks.size();
    }
}

