package org.cloudstore.worker.inventory;

import java.util.List;

/**
 * A page of chunk metadata together with the identity and address of its worker.
 */
public record WorkerChunkInventoryResponse(
        String workerId,
        String workerAddress,
        int page,
        int pageSize,
        long totalChunks,
        int totalPages,
        List<WorkerChunkInventoryItem> chunks)
{
}
