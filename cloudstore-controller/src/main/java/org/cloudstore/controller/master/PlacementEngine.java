package org.cloudstore.controller.master;

import org.cloudstore.controller.model.WorkerNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Selects distinct, live workers for storing chunk replicas based on availability and capacity.
 */
@Service
public class PlacementEngine
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PlacementEngine.class);

    private final WorkerRegistryService workerRegistry;
    private final int replicationFactor;

    public PlacementEngine(
            WorkerRegistryService workerRegistry,
            @Value("${controller.cluster.replication-factor:2}") int replicationFactor)
    {
        this.workerRegistry = workerRegistry;
        this.replicationFactor = replicationFactor;
    }

    public int getReplicationFactor()
    {
        return replicationFactor;
    }

    /**
     * Chooses N distinct UP workers with sufficient capacity for a chunk of the given size.
     * Excludes any worker IDs passed in excludedWorkerIds (useful when finding a replacement worker).
     */
    public List<WorkerNode> selectWorkersForChunk(long chunkSize, Set<String> excludedWorkerIds)
    {
        return selectWorkersForChunk(this.replicationFactor, chunkSize, excludedWorkerIds);
    }

    /**
     * Chooses count distinct UP workers with sufficient capacity.
     */
    public List<WorkerNode> selectWorkersForChunk(int count, long chunkSize, Set<String> excludedWorkerIds)
    {
        List<WorkerNode> activeWorkers = workerRegistry.getActiveWorkers().stream()
                .filter(w -> !excludedWorkerIds.contains(w.getWorkerId()))
                .filter(w -> w.getUnassignedCapacityBytes() >= chunkSize)
                .sorted(Comparator.comparingLong(WorkerNode::getUnassignedCapacityBytes).reversed())
                .collect(Collectors.toList());

        if (activeWorkers.size() < count)
        {
            LOGGER.warn("Placement failed: requested {} workers, but only {} eligible UP workers found",
                    count, activeWorkers.size());
            throw new IllegalStateException(
                    "Insufficient storage workers available: required " + count + ", available " + activeWorkers.size());
        }

        return activeWorkers.subList(0, count);
    }

    /**
     * Selects a single healthy replacement worker for an existing chunk.
     */
    public Optional<WorkerNode> selectReplacementWorker(long chunkSize, Set<String> existingWorkerIds)
    {
        try
        {
            List<WorkerNode> selected = selectWorkersForChunk(1, chunkSize, existingWorkerIds);
            return selected.isEmpty() ? Optional.empty() : Optional.of(selected.get(0));
        }
        catch (IllegalStateException e)
        {
            return Optional.empty();
        }
    }
}

