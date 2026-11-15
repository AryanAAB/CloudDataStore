package org.cloudstore.worker.data.constants;

/**
 * JPQL queries for chunk metadata operations.
 */
public final class ChunkFileMetadataQueries
{
    public static final String FIND_INTEGRITY_CHECK_BATCH =
            "select metadata from ChunkFileMetadata metadata "
                    + "order by case when metadata.lastIntegrityCheckedAt is null then 0 else 1 end, "
                    + "metadata.lastIntegrityCheckedAt, metadata.fileId, metadata.chunkId";

    private ChunkFileMetadataQueries()
    {
    }
}
