package org.cloudstore.controller.dto;

import java.util.Map;

/**
 * Request payload for Step 1 of the upload protocol.
 */
public record SessionInitiateRequest(
        String name,
        long size,
        String contentType,
        Map<String, String> metadata)
{
}

