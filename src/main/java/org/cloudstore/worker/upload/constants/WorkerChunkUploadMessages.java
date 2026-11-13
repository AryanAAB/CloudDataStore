package org.cloudstore.worker.upload.constants;

/**
 * User-facing messages for worker chunk upload failures.
 */
public final class WorkerChunkUploadMessages
{
    public static final String INVALID_REQUEST =
            "Chunk upload request contains invalid identifiers, key, size, or checksum";
    public static final String RESERVATION_REQUIRED = "No active reservation matches this chunk and key";
    public static final String CHUNK_INTEGRITY_MISMATCH =
            "Uploaded chunk size or SHA-256 checksum does not match the expected value";
    public static final String STORAGE_PATH_OUTSIDE_ROOT =
            "Chunk storage path is outside the configured storage directory";
    public static final String CHUNK_METADATA_CHANGED =
            "Chunk metadata changed while deletion was in progress";

    private WorkerChunkUploadMessages()
    {
    }
}
