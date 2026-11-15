package org.cloudstore.controller.master;

import org.cloudstore.controller.model.*;
import org.cloudstore.controller.proxy.WorkerChunkClient;
import org.cloudstore.controller.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ObjectDeletionServiceTest
{
    private StoredObjectRepository storedObjectRepository;
    private ChunkRecordRepository chunkRecordRepository;
    private ChunkReplicaRepository chunkReplicaRepository;
    private PendingDeletionRepository pendingDeletionRepository;
    private WorkerRegistryService workerRegistry;
    private StorageAccountingService storageAccounting;
    private WorkerChunkClient workerChunkClient;

    private ObjectDeletionService deletionService;

    @BeforeEach
    void setUp()
    {
        storedObjectRepository = mock(StoredObjectRepository.class);
        chunkRecordRepository = mock(ChunkRecordRepository.class);
        chunkReplicaRepository = mock(ChunkReplicaRepository.class);
        pendingDeletionRepository = mock(PendingDeletionRepository.class);
        workerRegistry = mock(WorkerRegistryService.class);
        storageAccounting = mock(StorageAccountingService.class);
        workerChunkClient = mock(WorkerChunkClient.class);

        deletionService = new ObjectDeletionService(
                storedObjectRepository,
                chunkRecordRepository,
                chunkReplicaRepository,
                pendingDeletionRepository,
                workerRegistry,
                storageAccounting,
                workerChunkClient);
    }

    @Test
    void deleteObject_notFound_returnsFalse()
    {
        when(storedObjectRepository.findById("missing")).thenReturn(Optional.empty());
        assertFalse(deletionService.deleteObject("missing"));
    }

    @Test
    void deleteObject_alreadyDeleted_returnsFalse()
    {
        StoredObject obj = new StoredObject("obj-1", "doc.pdf", 100L, "application/pdf", 1);
        obj.setStatus(StoredObject.STATUS_DELETED);
        when(storedObjectRepository.findById("obj-1")).thenReturn(Optional.of(obj));

        assertFalse(deletionService.deleteObject("obj-1"));
    }

    @Test
    void deleteObject_success_refundsStorageAndDispatchesWorkerDeletion()
    {
        String objectId = "obj-1";
        StoredObject obj = new StoredObject(objectId, "doc.pdf", 2048L, "application/pdf", 2);
        obj.setStatus(StoredObject.STATUS_COMMITTED);
        when(storedObjectRepository.findById(objectId)).thenReturn(Optional.of(obj));

        ChunkRecord chunk0 = new ChunkRecord(objectId + "_chunk_0", objectId, 0, 1024L, "hash0");
        ChunkRecord chunk1 = new ChunkRecord(objectId + "_chunk_1", objectId, 1, 1024L, "hash1");
        when(chunkRecordRepository.findByObjectIdOrderByChunkIndexAsc(objectId))
                .thenReturn(List.of(chunk0, chunk1));

        ChunkReplica r0_w1 = new ChunkReplica(chunk0.getChunkId(), "worker-1");
        ChunkReplica r0_w2 = new ChunkReplica(chunk0.getChunkId(), "worker-2");
        when(chunkReplicaRepository.findByChunkId(chunk0.getChunkId())).thenReturn(List.of(r0_w1, r0_w2));

        ChunkReplica r1_w1 = new ChunkReplica(chunk1.getChunkId(), "worker-1");
        ChunkReplica r1_w2 = new ChunkReplica(chunk1.getChunkId(), "worker-2");
        when(chunkReplicaRepository.findByChunkId(chunk1.getChunkId())).thenReturn(List.of(r1_w1, r1_w2));

        // worker-1 is UP, worker-2 is DOWN
        WorkerNode w1 = new WorkerNode("worker-1", "http://worker-1:8080", 10_000L);
        WorkerNode w2 = new WorkerNode("worker-2", "http://worker-2:8080", 10_000L);
        w2.setStatus(WorkerNode.STATUS_DOWN);
        when(workerRegistry.getWorker("worker-1")).thenReturn(Optional.of(w1));
        when(workerRegistry.getWorker("worker-2")).thenReturn(Optional.of(w2));

        when(workerChunkClient.deleteChunk(anyString(), anyString(), anyString(), anyString())).thenReturn(true);

        boolean deleted = deletionService.deleteObject(objectId);

        assertTrue(deleted);
        assertEquals(StoredObject.STATUS_DELETED, obj.getStatus());
        verify(storedObjectRepository).save(obj);

        // Verify quota refund was called for all chunk replicas (4 times)
        verify(storageAccounting, times(4)).releaseCapacity(anyString(), eq(1024L));

        // Verify pending tombstone saved for worker-2 which is DOWN
        verify(pendingDeletionRepository, atLeastOnce()).save(any(PendingDeletion.class));

        // Verify chunk metadata cleanup
        verify(chunkReplicaRepository).deleteByChunkId(chunk0.getChunkId());
        verify(chunkReplicaRepository).deleteByChunkId(chunk1.getChunkId());
        verify(chunkRecordRepository).deleteByObjectId(objectId);
    }
}

