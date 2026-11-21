package org.cloudstore.controller.master;

import org.cloudstore.controller.model.WorkerNode;
import org.cloudstore.controller.repository.WorkerNodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StorageAccountingServiceTest
{
    private WorkerNodeRepository workerRepository;
    private StorageAccountingService accountingService;

    @BeforeEach
    void setUp()
    {
        workerRepository = Mockito.mock(WorkerNodeRepository.class);
        accountingService = new StorageAccountingService(workerRepository);
    }

    @Test
    void reserveCapacity_success()
    {
        WorkerNode worker = new WorkerNode("worker-1", "http://worker-1:8080", 1000L);
        when(workerRepository.findById("worker-1")).thenReturn(Optional.of(worker));

        boolean reserved = accountingService.reserveCapacity("worker-1", 400L);

        assertTrue(reserved);
        assertEquals(600L, worker.getUnassignedCapacityBytes());
        verify(workerRepository).save(worker);
    }

    @Test
    void reserveCapacity_insufficientStorage_fails()
    {
        WorkerNode worker = new WorkerNode("worker-1", "http://worker-1:8080", 500L);
        when(workerRepository.findById("worker-1")).thenReturn(Optional.of(worker));

        boolean reserved = accountingService.reserveCapacity("worker-1", 600L);

        assertFalse(reserved);
        assertEquals(500L, worker.getUnassignedCapacityBytes());
        verify(workerRepository, never()).save(any());
    }

    @Test
    void releaseCapacity_restoresStorage()
    {
        WorkerNode worker = new WorkerNode("worker-1", "http://worker-1:8080", 1000L);
        worker.setUnassignedCapacityBytes(400L);
        when(workerRepository.findById("worker-1")).thenReturn(Optional.of(worker));

        accountingService.releaseCapacity("worker-1", 300L);

        assertEquals(700L, worker.getUnassignedCapacityBytes());
        verify(workerRepository).save(worker);
    }
}

