package org.cloudstore.worker.external;

/**
 * JSON payload sent to the master when a stored chunk fails an integrity scan.
 */
public record MasterChunkCorruptionReport(String fileId, String chunkId, String reason)
{
}
