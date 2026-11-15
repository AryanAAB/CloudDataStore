package org.cloudstore.controller.dto;

import java.time.Instant;

/**
 * Standard structured error payload returned across all API endpoints.
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path)
{
}

