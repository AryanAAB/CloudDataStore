package org.cloudstore.worker.integrity.constants;

/**
 * Log templates used by chunk integrity reporting.
 */
public final class WorkerChunkIntegrityLogMessages
{
    public static final String CORRUPTION_FOUND =
            "Chunk integrity failure for file {} chunk {}: {}";

    private WorkerChunkIntegrityLogMessages()
    {
    }
}
