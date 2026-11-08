package org.cloudstore.worker.data.constants;

/**
 * Database identifiers for persisted worker chunk-file metadata.
 */
public final class ChunkFileMetadataConstants
{
    public static final String TABLE_NAME = "chunk_file_metadata";
    public static final String FILE_ID_COLUMN = "file_id";
    public static final String CHUNK_ID_COLUMN = "chunk_id";
    public static final String FILE_LOCATION_COLUMN = "file_location";
    public static final String FILE_SIZE_COLUMN = "file_size";
    public static final String CHECKSUM_COLUMN = "checksum";
    public static final int FILE_LOCATION_COLUMN_LENGTH = 2048;
    public static final int CHECKSUM_COLUMN_LENGTH = 64;

    private ChunkFileMetadataConstants()
    {
    }
}
