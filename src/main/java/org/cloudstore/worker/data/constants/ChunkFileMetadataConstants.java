package org.cloudstore.worker.data.constants;

import org.cloudstore.worker.integrity.constants.ChecksumConstants;

/**
 * Database identifiers for persisted worker chunk-file metadata.
 */
public final class ChunkFileMetadataConstants
{
    public static final String TABLE_NAME = "chunk_file_metadata";
    public static final String FILE_LOCATION_COLUMN = "file_location";
    public static final String FILE_SIZE_COLUMN = "file_size";
    public static final String CHECKSUM_COLUMN = "checksum";
    public static final String LAST_INTEGRITY_CHECKED_AT_COLUMN = "last_integrity_checked_at";
    public static final String INTEGRITY_SCAN_INDEX = "idx_chunk_metadata_integrity_scan";
    public static final String INTEGRITY_SCAN_INDEX_COLUMNS =
            LAST_INTEGRITY_CHECKED_AT_COLUMN + ", " + DatabaseColumnConstants.FILE_ID
                    + ", " + DatabaseColumnConstants.CHUNK_ID;
    public static final int FILE_LOCATION_COLUMN_LENGTH = 2048;
    public static final int CHECKSUM_COLUMN_LENGTH = ChecksumConstants.SHA_256_HEX_LENGTH;

    private ChunkFileMetadataConstants()
    {
    }
}
