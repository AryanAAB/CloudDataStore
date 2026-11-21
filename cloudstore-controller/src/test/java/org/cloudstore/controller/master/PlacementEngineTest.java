package org.cloudstore.controller.master;

import org.cloudstore.controller.model.WorkerNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class PlacementEngineTest
{
    private WorkerRegistryService workerRegistry;
    private PlacementEngine placementEngine;

    @BeforeEach
    void setUp()
    {
        workerRegistry = Mockito.mock(WorkerRegistryService.class);
        placementEngine = new PlacementEngine(workerRegistry, 2);
    }

    @Test
    void selectWorkersForChunk_picksTwoDistinctWorkersWithMostCapacity()
    {
        WorkerNode w1 = new WorkerNode("worker-1", "http://worker-1:8080", 10_000_000L);
        WorkerNode w2 = new WorkerNode("worker-2", "http://worker-2:8080", 20_000_000L);
        WorkerNode w3 = new WorkerNode("worker-3", "http://worker-3:8080", 30_000_000L);

        when(workerRegistry.getActiveWorkers()).thenReturn(List.of(w1, w2, w3));

        List<WorkerNode> selected = placementEngine.selectWorkersForChunk(1_048_576L, Collections.emptySet());

        assertEquals(2, selected.size());
        assertEquals("worker-3", selected.get(0).getWorkerId());
        assertEquals("worker-2", selected.get(1).getWorkerId());
    }

    @Test
    void selectReplacementWorker_excludesFailedWorkers()
    {
        WorkerNode w1 = new WorkerNode("worker-1", "http://worker-1:8080", 10_000_000L);
        WorkerNode w2 = new WorkerNode("worker-2", "http://worker-2:8080", 20_000_000L);
        WorkerNode w3 = new WorkerNode("worker-3", "http://worker-3:8080", 30_000_000L);

        when(workerRegistry.getActiveWorkers()).thenReturn(List.of(w1, w2, w3));

        // worker-3 and worker-2 already attempted, pick replacement
        Optional<WorkerNode> replacement = placementEngine.selectReplacementWorker(
                1_048_576L, Set.of("worker-3", "worker-2"));

        assertTrue(replacement.isPresent());
        assertEquals("worker-1", replacement.get().getWorkerId());
    }

    @Test
    void selectWorkersForChunk_insufficientWorkers_throws()
    {
        WorkerNode w1 = new WorkerNode("worker-1", "http://worker-1:8080", 10_000_000L);
        when(workerRegistry.getActiveWorkers()).thenReturn(List.of(w1));

        assertThrows(IllegalStateException.class, () ->
                placementEngine.selectWorkersForChunk(1_048_576L, Collections.emptySet()));
    }
}

