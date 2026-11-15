package org.cloudstore.controller.master;

import org.cloudstore.controller.model.WorkerNode;
import org.cloudstore.controller.repository.WorkerNodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Manages atomic capacity reservations and releases on storage workers.
 */
@Service
public class StorageAccountingService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(StorageAccountingService.class);

    private final WorkerNodeRepository workerRepository;

    public StorageAccountingService(WorkerNodeRepository workerRepository)
    {
        this.workerRepository = workerRepository;
    }

    /**
     * Atomically reserves the requested byte capacity on a specific worker.
     * Returns true if reservation was granted, false if insufficient space.
     */
    @Transactional
    public boolean reserveCapacity(String workerId, long bytesToReserve)
    {
        Optional<WorkerNode> opt = workerRepository.findById(workerId);
        if (opt.isEmpty())
        {
            return false;
        }
        WorkerNode node = opt.get();
        if (node.getUnassignedCapacityBytes() < bytesToReserve)
        {
            LOGGER.warn("Worker {} has insufficient unassigned capacity: required {}, available {}",
                    workerId, bytesToReserve, node.getUnassignedCapacityBytes());
            return false;
        }
        node.setUnassignedCapacityBytes(node.getUnassignedCapacityBytes() - bytesToReserve);
        workerRepository.save(node);
        LOGGER.debug("Reserved {} bytes on worker {}. Remaining: {}",
                bytesToReserve, workerId, node.getUnassignedCapacityBytes());
        return true;
    }

    /**
     * Releases previously reserved bytes back to a worker's unassigned pool.
     */
    @Transactional
    public void releaseCapacity(String workerId, long bytesToRelease)
    {
        workerRepository.findById(workerId).ifPresent(node -> {
            long newCapacity = Math.min(
                    node.getTotalCapacityBytes(),
                    node.getUnassignedCapacityBytes() + bytesToRelease);
            node.setUnassignedCapacityBytes(newCapacity);
            workerRepository.save(node);
            LOGGER.debug("Released {} bytes back to worker {}. Current unassigned: {}",
                    bytesToRelease, workerId, newCapacity);
        });
    }

    /**
     * Batch release of capacities.
     */
    @Transactional
    public void releaseBatch(Map<String, Long> workerReleases)
    {
        workerReleases.forEach(this::releaseCapacity);
    }

    /**
     * Returns total cluster available unassigned capacity across all UP workers.
     */
    public long getTotalAvailableCapacity()
    {
        return workerRepository.findByStatus(WorkerNode.STATUS_UP).stream()
                .mapToLong(WorkerNode::getUnassignedCapacityBytes)
                .sum();
    }
}
