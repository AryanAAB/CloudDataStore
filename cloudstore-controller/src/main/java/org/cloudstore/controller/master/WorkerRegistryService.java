package org.cloudstore.controller.master;

import org.cloudstore.controller.model.WorkerNode;
import org.cloudstore.controller.repository.WorkerNodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Manages worker node registration, heartbeat liveness, and watchdog eviction.
 */
@Service
public class WorkerRegistryService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(WorkerRegistryService.class);

    private final WorkerNodeRepository workerRepository;
    private final Duration heartbeatTimeout;
    private final long defaultWorkerCapacity;

    public WorkerRegistryService(
            WorkerNodeRepository workerRepository,
            @Value("${controller.worker.heartbeat-timeout-seconds:10}") long timeoutSeconds,
            @Value("${controller.worker.default-capacity-bytes:10737418240}") long defaultCapacity)
    {
        this.workerRepository = workerRepository;
        this.heartbeatTimeout = Duration.ofSeconds(timeoutSeconds);
        this.defaultWorkerCapacity = defaultCapacity;
    }

    @Transactional
    public void registerWorker(String workerId, String address)
    {
        Optional<WorkerNode> existing = workerRepository.findById(workerId);
        if (existing.isPresent())
        {
            WorkerNode node = existing.get();
            node.setAddress(address);
            node.setStatus(WorkerNode.STATUS_UP);
            node.setLastHeartbeat(Instant.now());
            workerRepository.save(node);
            LOGGER.info("Worker {} re-registered at {}", workerId, address);
        }
        else
        {
            WorkerNode node = new WorkerNode(workerId, address, defaultWorkerCapacity);
            workerRepository.save(node);
            LOGGER.info("Worker {} registered for the first time at {} with {} bytes capacity",
                    workerId, address, defaultWorkerCapacity);
        }
    }

    @Transactional
    public void recordHeartbeat(String workerId)
    {
        workerRepository.findById(workerId).ifPresentOrElse(node -> {
            node.setLastHeartbeat(Instant.now());
            if (!node.isUp())
            {
                node.setStatus(WorkerNode.STATUS_UP);
                LOGGER.info("Worker {} transitioned from DOWN to UP on heartbeat", workerId);
            }
            workerRepository.save(node);
        }, () -> LOGGER.warn("Received heartbeat from unknown worker {}", workerId));
    }

    @Scheduled(fixedDelayString = "${controller.worker.watchdog-interval-ms:2000}")
    @Transactional
    public void checkWorkerLiveness()
    {
        Instant cutoff = Instant.now().minus(heartbeatTimeout);
        List<WorkerNode> upWorkers = workerRepository.findByStatus(WorkerNode.STATUS_UP);
        for (WorkerNode worker : upWorkers)
        {
            if (worker.getLastHeartbeat().isBefore(cutoff))
            {
                worker.setStatus(WorkerNode.STATUS_DOWN);
                workerRepository.save(worker);
                LOGGER.warn("Watchdog marked worker {} DOWN (last heartbeat at {})",
                        worker.getWorkerId(), worker.getLastHeartbeat());
            }
        }
    }

    public List<WorkerNode> getActiveWorkers()
    {
        return workerRepository.findByStatus(WorkerNode.STATUS_UP);
    }

    public Optional<WorkerNode> getWorker(String workerId)
    {
        return workerRepository.findById(workerId);
    }

    public List<WorkerNode> getAllWorkers()
    {
        return workerRepository.findAll();
    }
}

