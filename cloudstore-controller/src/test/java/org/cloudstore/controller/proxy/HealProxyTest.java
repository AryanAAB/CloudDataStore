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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HealProxyTest
{
    private WorkerRegistryService workerRegistry;
    private StorageAccountingService storageAccounting;
    private PlacementEngine placementEngine;
    private WorkerChunkClient workerChunkClient;
    private ChunkRecordRepository chunkRecordRepository;
    private ChunkReplicaRepository chunkReplicaRepository;

    private HealProxy healProxy;

    @BeforeEach
    void setUp()
    {
        workerRegistry = mock(WorkerRegistryService.class);
        storageAccounting = mock(StorageAccountingService.class);
        placementEngine = mock(PlacementEngine.class);
        workerChunkClient = mock(WorkerChunkClient.class);
        chunkRecordRepository = mock(ChunkRecordRepository.class);
        chunkReplicaRepository = mock(ChunkReplicaRepository.class);

        healProxy = new HealProxy(
                workerRegistry,
                storageAccounting,
                placementEngine,
                workerChunkClient,
                chunkRecordRepository,
                chunkReplicaRepository);
    }

    @Test
    void repairChunk_success_reReplicatesToNewWorker()
    {
        String chunkId = "obj-1_chunk_0";
        byte[] expectedBytes = "VerifiedHealContent".getBytes(StandardCharsets.UTF_8);
        String expectedHash = ChunkHashUtils.sha256Hex(expectedBytes);

        ChunkRecord chunk = new ChunkRecord(chunkId, "obj-1", 0, expectedBytes.length, expectedHash);
        when(chunkRecordRepository.findById(chunkId)).thenReturn(Optional.of(chunk));

        // worker-1 is UP with valid data; worker-2 is DOWN
        ChunkReplica r1 = new ChunkReplica(chunkId, "worker-1");
        ChunkReplica r2 = new ChunkReplica(chunkId, "worker-2");
        when(chunkReplicaRepository.findByChunkId(chunkId)).thenReturn(List.of(r1, r2));

        WorkerNode w1 = new WorkerNode("worker-1", "http://worker-1:8080", 10_000L);
        WorkerNode w2 = new WorkerNode("worker-2", "http://worker-2:8080", 10_000L);
        w2.setStatus(WorkerNode.STATUS_DOWN);
        WorkerNode w3 = new WorkerNode("worker-3", "http://worker-3:8080", 10_000L); // replacement target

        when(workerRegistry.getWorker("worker-1")).thenReturn(Optional.of(w1));
        when(workerRegistry.getWorker("worker-2")).thenReturn(Optional.of(w2));
        when(workerRegistry.getWorker("worker-3")).thenReturn(Optional.of(w3));

        // Source read from worker-1 succeeds
        when(workerChunkClient.downloadChunk(eq(w1.getAddress()), eq("obj-1"), eq(chunkId), anyString()))
                .thenReturn(expectedBytes);

        // Placement selects worker-3 as replacement
        when(placementEngine.selectReplacementWorker(eq((long) expectedBytes.length), anySet()))
                .thenReturn(Optional.of(w3));

        when(storageAccounting.reserveCapacity("worker-3", expectedBytes.length)).thenReturn(true);
        when(workerChunkClient.reserveChunk(eq(w3.getAddress()), eq("obj-1"), eq(chunkId), anyString()))
                .thenReturn(true);
        when(workerChunkClient.uploadChunk(
                eq(w3.getAddress()), eq("obj-1"), eq(chunkId), anyString(), eq(expectedBytes), eq(expectedHash)))
                .thenReturn(true);

        boolean repaired = healProxy.repairChunk(chunkId);

        assertTrue(repaired);
        verify(chunkReplicaRepository).save(any(ChunkReplica.class));
    }

    @Test
    void repairChunk_noLiveHealthySource_fails()
    {
        String chunkId = "obj-2_chunk_0";
        ChunkRecord chunk = new ChunkRecord(chunkId, "obj-2", 0, 100L, "somehash");
        when(chunkRecordRepository.findById(chunkId)).thenReturn(Optional.of(chunk));

        // All replicas are corrupted or down
        ChunkReplica r1 = new ChunkReplica(chunkId, "worker-1");
        r1.setReplicaState(ChunkReplica.STATE_CORRUPT);
        when(chunkReplicaRepository.findByChunkId(chunkId)).thenReturn(List.of(r1));

        boolean repaired = healProxy.repairChunk(chunkId);

        assertFalse(repaired);
        verify(placementEngine, never()).selectReplacementWorker(anyLong(), anySet());
    }
}
