package org.cloudstore.controller.dto;

import java.time.Instant;

/**
 * Response payload returning the upload authorization key and metadata for Step 2.
 */
public record SessionInitiateResponse(
        String objectId,
        String sessionId,
        String sessionKey,
        int chunkSize,
        int totalChunks,
        String uploadUrl,
        Instant expiresAt)
{
}

