package org.cloudstore.worker.inventory;

import java.time.Instant;

/**
 * Public inventory metadata for one chunk stored on this worker.
 */
public record WorkerChunkInventoryItem(
        String fileId,
        String chunkId,
        long fileSize,
        String checksum,
        Instant lastIntegrityCheckedAt)
{
}
