package org.cloudstore.worker.upload.constants;

/**
 * Log messages for worker chunk uploads.
 */
public final class WorkerChunkUploadLogMessages
{
    public static final String CHUNK_STORED = "Stored chunk for file {} and chunk {} ({} bytes)";
    public static final String HEARTBEAT_FAILED =
            "Reservation heartbeat failed for file {} and chunk {}";
    public static final String OLD_CHUNK_FILE_CLEANUP_FAILED =
            "Could not remove replaced chunk file at {}";
    public static final String FILE_METADATA_STORE_FAILED =
            "Could not commit file metadata for file {} and chunk {}";
    public static final String NEW_CHUNK_FILE_CLEANUP_FAILED =
            "Could not remove unrecorded chunk file at {}";

    private WorkerChunkUploadLogMessages()
    {
    }
}
