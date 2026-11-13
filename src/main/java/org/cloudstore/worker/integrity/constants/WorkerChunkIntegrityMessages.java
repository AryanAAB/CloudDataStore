package org.cloudstore.worker.integrity.constants;

/**
 * Reason descriptions attached to chunk integrity findings.
 */
public final class WorkerChunkIntegrityMessages
{
    public static final String SIZE_MISMATCH = "stored size differs from metadata";
    public static final String CHECKSUM_MISMATCH = "stored checksum differs from metadata";
    public static final String FILE_UNAVAILABLE = "stored chunk is missing or unreadable";

    private WorkerChunkIntegrityMessages()
    {
    }
}
