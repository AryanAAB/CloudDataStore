package org.cloudstore.controller.dto;

import java.time.Instant;

/**
 * Summary representation for object listing.
 */
public record StoredObjectSummary(
        String objectId,
        String name,
        long size,
        String contentType,
        Instant createdAt)
{
}

