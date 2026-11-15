package org.cloudstore.controller.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Public response model for a stored object, including system metadata,
 * custom metadata, and chunk storage locations.
 */
public record StoredObjectResponse(
        String objectId,
        String name,
        long size,
        String contentType,
        String checksum,
        int totalChunks,
        String status,
        Instant createdAt,
        Instant updatedAt,
        Map<String, String> metadata,
        List<ChunkLocationSummary> chunks)
{
    public record ChunkLocationSummary(
            String chunkId,
            int chunkIndex,
            long size,
            String checksum,
            List<String> locations)
    {
    }
}
