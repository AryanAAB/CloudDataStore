package org.cloudstore.controller.dto;

import java.time.Instant;

/**
 * Status of an active or completed upload session, used for resumability queries.
 */
public record SessionStatusResponse(
        String sessionId,
        String objectId,
        String status,
        int totalChunks,
        int nextExpectedChunkIndex,
        Instant expiresAt)
{
}

