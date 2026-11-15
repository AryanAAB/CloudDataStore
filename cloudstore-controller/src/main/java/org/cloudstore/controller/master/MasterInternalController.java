package org.cloudstore.controller.master;

import org.cloudstore.common.dto.MasterChunkCorruptionReport;
import org.cloudstore.common.dto.MasterWorkerHeartbeat;
import org.cloudstore.common.dto.MasterWorkerRegistration;
import org.cloudstore.controller.model.ChunkReplica;
import org.cloudstore.controller.repository.ChunkReplicaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * Controller endpoints handling worker registration, heartbeats, and corruption notifications.
 */
@RestController
public class MasterInternalController
{
    private static final Logger LOGGER = LoggerFactory.getLogger(MasterInternalController.class);

    private final WorkerRegistryService workerRegistry;
    private final ChunkReplicaRepository chunkReplicaRepository;

    public MasterInternalController(
            WorkerRegistryService workerRegistry,
            ChunkReplicaRepository chunkReplicaRepository)
    {
        this.workerRegistry = workerRegistry;
        this.chunkReplicaRepository = chunkReplicaRepository;
    }

    @PostMapping("/internal/workers/register")
    public ResponseEntity<Void> registerWorker(@RequestBody MasterWorkerRegistration registration)
    {
        LOGGER.info("Registration request received from worker {} at {}",
                registration.workerId(), registration.address());
        workerRegistry.registerWorker(registration.workerId(), registration.address());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/internal/workers/heartbeat")
    public ResponseEntity<Void> receiveHeartbeat(@RequestBody MasterWorkerHeartbeat heartbeat)
    {
        workerRegistry.recordHeartbeat(heartbeat.workerId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/v1/workers/{workerId}/chunks/corruption-reports")
    public ResponseEntity<Void> receiveCorruptionReport(
            @PathVariable String workerId,
            @RequestBody MasterChunkCorruptionReport report)
    {
        LOGGER.warn("Received corruption report from worker {} for chunk {}: {}",
                workerId, report.chunkId(), report.reason());

        Optional<ChunkReplica> replicaOpt =
                chunkReplicaRepository.findByChunkIdAndWorkerId(report.chunkId(), workerId);
        replicaOpt.ifPresent(replica -> {
            replica.setReplicaState(ChunkReplica.STATE_CORRUPT);
            chunkReplicaRepository.save(replica);
            LOGGER.info("Marked replica of chunk {} on worker {} as CORRUPT",
                    report.chunkId(), workerId);
        });

        return ResponseEntity.ok().build();
    }
}

