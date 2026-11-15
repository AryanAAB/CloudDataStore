package org.cloudstore.controller.master;

import org.cloudstore.controller.model.ChunkRecord;
import org.cloudstore.controller.model.ChunkReplica;
import org.cloudstore.controller.model.StoredObject;
import org.cloudstore.controller.model.WorkerNode;
import org.cloudstore.controller.proxy.HealProxy;
import org.cloudstore.controller.repository.ChunkRecordRepository;
import org.cloudstore.controller.repository.ChunkReplicaRepository;
import org.cloudstore.controller.repository.StoredObjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ReconcilerServiceTest
{
    private StoredObjectRepository storedObjectRepository;
    private ChunkRecordRepository chunkRecordRepository;
    private ChunkReplicaRepository chunkReplicaRepository;
    private WorkerRegistryService workerRegistry;
    private HealProxy healProxy;
    private PlacementEngine placementEngine;

    private ReconcilerService reconcilerService;

    @BeforeEach
    void setUp()
    {
        storedObjectRepository = mock(StoredObjectRepository.class);
        chunkRecordRepository = mock(ChunkRecordRepository.class);
        chunkReplicaRepository = mock(ChunkReplicaRepository.class);
        workerRegistry = mock(WorkerRegistryService.class);
        healProxy = mock(HealProxy.class);
        placementEngine = mock(PlacementEngine.class);

        when(placementEngine.getReplicationFactor()).thenReturn(2);

        reconcilerService = new ReconcilerService(
                storedObjectRepository,
                chunkRecordRepository,
                chunkReplicaRepository,
                workerRegistry,
                healProxy,
                placementEngine);
    }

    @Test
    void runReconciliation_triggersHealingWhenUnderReplicated() throws InterruptedException
    {
        StoredObject obj = new StoredObject("obj-1", "file.bin", 1000L, "application/octet-stream", 1);
        obj.setStatus(StoredObject.STATUS_COMMITTED);
        when(storedObjectRepository.findByStatus(StoredObject.STATUS_COMMITTED)).thenReturn(List.of(obj));

        ChunkRecord chunk0 = new ChunkRecord("obj-1_chunk_0", "obj-1", 0, 1000L, "hash0");
        when(chunkRecordRepository.findByObjectIdOrderByChunkIndexAsc("obj-1")).thenReturn(List.of(chunk0));

        // worker-1 is UP, worker-2 is DOWN -> only 1 live copy (required: 2)
        ChunkReplica r1 = new ChunkReplica("obj-1_chunk_0", "worker-1");
        ChunkReplica r2 = new ChunkReplica("obj-1_chunk_0", "worker-2");
        when(chunkReplicaRepository.findByChunkId("obj-1_chunk_0")).thenReturn(List.of(r1, r2));

        WorkerNode w1 = new WorkerNode("worker-1", "http://worker-1:8080", 10_000L);
        WorkerNode w2 = new WorkerNode("worker-2", "http://worker-2:8080", 10_000L);
        w2.setStatus(WorkerNode.STATUS_DOWN);

        when(workerRegistry.getWorker("worker-1")).thenReturn(Optional.of(w1));
        when(workerRegistry.getWorker("worker-2")).thenReturn(Optional.of(w2));

        reconcilerService.runReconciliation();

        // Give the virtual thread a moment to dispatch
        Thread.sleep(100);

        verify(healProxy).repairChunk("obj-1_chunk_0");
    }

    @Test
    void runReconciliation_doesNotTriggerWhenFullyReplicated()
    {
        StoredObject obj = new StoredObject("obj-2", "file.bin", 1000L, "application/octet-stream", 1);
        obj.setStatus(StoredObject.STATUS_COMMITTED);
        when(storedObjectRepository.findByStatus(StoredObject.STATUS_COMMITTED)).thenReturn(List.of(obj));

        ChunkRecord chunk0 = new ChunkRecord("obj-2_chunk_0", "obj-2", 0, 1000L, "hash0");
        when(chunkRecordRepository.findByObjectIdOrderByChunkIndexAsc("obj-2")).thenReturn(List.of(chunk0));

        // Both workers UP -> 2 live copies (required: 2)
        ChunkReplica r1 = new ChunkReplica("obj-2_chunk_0", "worker-1");
        ChunkReplica r2 = new ChunkReplica("obj-2_chunk_0", "worker-2");
        when(chunkReplicaRepository.findByChunkId("obj-2_chunk_0")).thenReturn(List.of(r1, r2));

        WorkerNode w1 = new WorkerNode("worker-1", "http://worker-1:8080", 10_000L);
        WorkerNode w2 = new WorkerNode("worker-2", "http://worker-2:8080", 10_000L);

        when(workerRegistry.getWorker("worker-1")).thenReturn(Optional.of(w1));
        when(workerRegistry.getWorker("worker-2")).thenReturn(Optional.of(w2));

        reconcilerService.runReconciliation();

        verify(healProxy, never()).repairChunk(anyString());
    }
}
