package org.cloudstore.worker.upload.constants;

/**
 * Log messages for worker chunk uploads.
 */
public final class WorkerChunkUploadLogMessages
{
    public static final String CHUNK_STORED = "Stored chunk for file {} and chunk {} ({} bytes)";
    public static final String HEARTBEAT_FAILED =
            "Reservation heartbeat failed for file {} and chunk {}";

    private WorkerChunkUploadLogMessages()
    {
    }
}
