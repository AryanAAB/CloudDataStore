package org.cloudstore.controller.proxy;

import org.cloudstore.common.util.ChunkHashUtils;
import org.cloudstore.controller.dto.SessionInitiateRequest;
import org.cloudstore.controller.dto.SessionInitiateResponse;
import org.cloudstore.controller.master.PlacementEngine;
import org.cloudstore.controller.master.StorageAccountingService;
import org.cloudstore.controller.master.WorkerRegistryService;
import org.cloudstore.controller.model.*;
import org.cloudstore.controller.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UProxyServiceTest
{
    private WorkerRegistryService workerRegistry;
    private StorageAccountingService storageAccounting;
    private PlacementEngine placementEngine;
    private WorkerChunkClient workerChunkClient;

    private StoredObjectRepository storedObjectRepository;
    private ChunkRecordRepository chunkRecordRepository;
    private ChunkReplicaRepository chunkReplicaRepository;
    private UploadSessionRepository uploadSessionRepository;

    private UProxyService uProxyService;

    @BeforeEach
    void setUp()
    {
        workerRegistry = mock(WorkerRegistryService.class);
        storageAccounting = mock(StorageAccountingService.class);
        placementEngine = mock(PlacementEngine.class);
        workerChunkClient = mock(WorkerChunkClient.class);

        storedObjectRepository = mock(StoredObjectRepository.class);
        chunkRecordRepository = mock(ChunkRecordRepository.class);
        chunkReplicaRepository = mock(ChunkReplicaRepository.class);
        uploadSessionRepository = mock(UploadSessionRepository.class);

        when(placementEngine.getReplicationFactor()).thenReturn(2);

        uProxyService = new UProxyService(
                workerRegistry,
                storageAccounting,
                placementEngine,
                workerChunkClient,
                storedObjectRepository,
                chunkRecordRepository,
                chunkReplicaRepository,
                uploadSessionRepository,
                1024, // 1 KB chunks for test convenience
                600L);
    }

    @Test
    void initiateSession_success()
    {
        WorkerNode w1 = new WorkerNode("worker-1", "http://worker-1:8080", 10_000L);
        WorkerNode w2 = new WorkerNode("worker-2", "http://worker-2:8080", 10_000L);
        when(workerRegistry.getActiveWorkers()).thenReturn(List.of(w1, w2));
        when(storageAccounting.getTotalAvailableCapacity()).thenReturn(20_000L);

        SessionInitiateRequest request = new SessionInitiateRequest(
                "test.txt", 1500L, "text/plain", Map.of("tag", "demo"));

        SessionInitiateResponse response = uProxyService.initiateSession(request);

        assertNotNull(response);
        assertNotNull(response.sessionId());
        assertNotNull(response.sessionKey());
        assertEquals(2, response.totalChunks()); // 1500 / 1024 = 2 chunks
        verify(storedObjectRepository).save(any(StoredObject.class));
        verify(uploadSessionRepository).save(any(UploadSession.class));
    }

    @Test
    void processStreamUpload_dualWritesToWorkersAndCommits() throws IOException
    {
        String sessionId = "sess-123";
        String sessionKey = "key-456";
        String objectId = "obj-789";

        UploadSession session = new UploadSession(
                sessionId, sessionKey, objectId, "file.bin", 500L, "application/octet-stream",
                1, Instant.now().plusSeconds(600), Collections.emptyMap());

        when(uploadSessionRepository.findBySessionIdAndSessionKey(sessionId, sessionKey))
                .thenReturn(Optional.of(session));

        StoredObject storedObj = new StoredObject(objectId, "file.bin", 500L, "application/octet-stream", 1);
        when(storedObjectRepository.findById(objectId)).thenReturn(Optional.of(storedObj));

        WorkerNode w1 = new WorkerNode("worker-1", "http://worker-1:8080", 10_000L);
        WorkerNode w2 = new WorkerNode("worker-2", "http://worker-2:8080", 10_000L);
        when(placementEngine.selectWorkersForChunk(eq(2), anyLong(), anySet()))
                .thenReturn(List.of(w1, w2));

        when(storageAccounting.reserveCapacity(anyString(), anyLong())).thenReturn(true);
        when(workerChunkClient.reserveChunk(anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        when(workerChunkClient.uploadChunk(anyString(), anyString(), anyString(), anyString(), any(), anyString()))
                .thenReturn(true);

        byte[] payload = "Hello Distributed Cloud Store".getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream in = new ByteArrayInputStream(payload);

        // Adjust session declared size to match payload
        session.setDeclaredSize(payload.length);

        StoredObject result = uProxyService.processStreamUpload(sessionId, sessionKey, in);

        assertNotNull(result);
        assertEquals(StoredObject.STATUS_COMMITTED, result.getStatus());
        assertEquals(payload.length, result.getSize());

        // Verify write was dispatched to both workers
        verify(workerChunkClient, times(2))
                .uploadChunk(anyString(), eq(objectId), contains("_chunk_0"), anyString(), any(), anyString());
        verify(chunkRecordRepository).save(any(ChunkRecord.class));
        verify(chunkReplicaRepository, times(2)).save(any(ChunkReplica.class));
    }

    @Test
    void streamDownload_fallsBackWhenFirstReplicaFails() throws IOException
    {
        String objectId = "obj-100";
        StoredObject obj = new StoredObject(objectId, "sample.txt", 12L, "text/plain", 1);
        obj.setStatus(StoredObject.STATUS_COMMITTED);
        when(storedObjectRepository.findById(objectId)).thenReturn(Optional.of(obj));

        byte[] expectedBytes = "FallbackData".getBytes(StandardCharsets.UTF_8);
        String expectedHash = ChunkHashUtils.sha256Hex(expectedBytes);

        ChunkRecord chunk = new ChunkRecord(objectId + "_chunk_0", objectId, 0, expectedBytes.length, expectedHash);
        when(chunkRecordRepository.findByObjectIdOrderByChunkIndexAsc(objectId)).thenReturn(List.of(chunk));

        ChunkReplica r1 = new ChunkReplica(chunk.getChunkId(), "worker-1");
        ChunkReplica r2 = new ChunkReplica(chunk.getChunkId(), "worker-2");
        when(chunkReplicaRepository.findByChunkId(chunk.getChunkId())).thenReturn(List.of(r1, r2));

        WorkerNode w1 = new WorkerNode("worker-1", "http://worker-1:8080", 1000L);
        WorkerNode w2 = new WorkerNode("worker-2", "http://worker-2:8080", 1000L);
        when(workerRegistry.getWorker("worker-1")).thenReturn(Optional.of(w1));
        when(workerRegistry.getWorker("worker-2")).thenReturn(Optional.of(w2));

        // Worker 1 returns corrupt bytes
        when(workerChunkClient.downloadChunk(eq(w1.getAddress()), eq(objectId), eq(chunk.getChunkId()), anyString()))
                .thenReturn("CorruptedData".getBytes(StandardCharsets.UTF_8));

        // Worker 2 returns correct bytes
        when(workerChunkClient.downloadChunk(eq(w2.getAddress()), eq(objectId), eq(chunk.getChunkId()), anyString()))
                .thenReturn(expectedBytes);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        uProxyService.streamDownload(objectId, out);

        assertEquals("FallbackData", out.toString(StandardCharsets.UTF_8));
        // Verify worker-1 replica was flagged as CORRUPT
        assertEquals(ChunkReplica.STATE_CORRUPT, r1.getReplicaState());
        verify(chunkReplicaRepository).save(r1);
    }
}

