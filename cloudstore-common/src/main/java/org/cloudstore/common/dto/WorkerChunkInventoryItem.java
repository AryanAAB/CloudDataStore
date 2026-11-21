package org.cloudstore.common.dto;

import java.time.Instant;

/**
 * Public inventory metadata for one chunk stored on a worker.
 */
public record WorkerChunkInventoryItem(
        String fileId,
        String chunkId,
        long fileSize,
        String checksum,
        Instant lastIntegrityCheckedAt)
{
}

