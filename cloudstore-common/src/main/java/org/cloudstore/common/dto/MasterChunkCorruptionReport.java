package org.cloudstore.common.dto;

/**
 * JSON payload sent to the master when a stored chunk fails an integrity scan.
 */
public record MasterChunkCorruptionReport(String fileId, String chunkId, String reason)
{
}

