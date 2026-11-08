package org.cloudstore.worker.upload.constants;

/**
 * Fixed identifiers used by the worker chunk upload implementation.
 */
public final class WorkerChunkUploadConstants
{
    public static final String CHUNK_UPLOAD_PATH = "/{fileId}/chunks/{chunkId}/upload";
    public static final String EXPECTED_CHUNK_SIZE_HEADER = "X-Chunk-Size";
    public static final String EXPECTED_CHUNK_SHA_256_HEADER = "X-Chunk-SHA256";
    public static final String SHA_256_ALGORITHM = "SHA-256";
    public static final String TEMP_FILE_PREFIX = "upload-";
    public static final String TEMP_FILE_SUFFIX = ".tmp";
    public static final String CHUNK_FILE_SUFFIX = ".chunk";
    public static final byte CHUNK_ID_SEPARATOR = 0;

    private WorkerChunkUploadConstants()
    {
    }
}
