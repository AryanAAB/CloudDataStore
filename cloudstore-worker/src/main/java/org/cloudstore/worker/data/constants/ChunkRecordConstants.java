package org.cloudstore.worker.data.constants;

/**
 * Database identifiers for worker chunk reservation records.
 */
public final class ChunkRecordConstants
{
    public static final String TABLE_NAME = "chunk_record";
    public static final String RESERVATION_KEY_COLUMN = "reservation_key";
    public static final String EXPIRES_AT_COLUMN = "expires_at";

    private ChunkRecordConstants()
    {
    }
}
