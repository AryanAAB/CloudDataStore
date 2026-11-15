package org.cloudstore.controller.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Public response model for a stored object.
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
        Map<String, String> metadata)
{
}

