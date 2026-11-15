package org.cloudstore.common.constants;

/**
 * Shared worker HTTP paths and header names.
 */
public final class WorkerApiConstants
{
    public static final String API_ROOT = "/worker/files";
    public static final String CHUNK_RESERVATION_PATH = "/{fileId}/chunks/{chunkId}/reservation";
    public static final String RESERVATION_KEY_HEADER = "X-Reservation-Key";
    public static final String CHUNK_SIZE_HEADER = "X-Chunk-Size";
    public static final String CHUNK_SHA256_HEADER = "X-Chunk-SHA256";
    public static final String CHUNK_PATH = "/{fileId}/chunks/{chunkId}";
    public static final String CHUNK_INVENTORY_PATH = "/chunks";
    public static final String PAGE_QUERY_PARAMETER = "page";
    public static final String PAGE_SIZE_QUERY_PARAMETER = "size";
    public static final String DEFAULT_PAGE_NUMBER = "0";

    private WorkerApiConstants()
    {
    }
}

