package org.cloudstore.worker.integrity;

/**
 * Boundary for reporting confirmed chunk corruption to the master.
 */
public interface ChunkCorruptionReporter
{
    /**
     * Reports the corrupted chunk; implementations should make retries idempotent.
     */
    void report(String fileId, String chunkId, String reason);
}
