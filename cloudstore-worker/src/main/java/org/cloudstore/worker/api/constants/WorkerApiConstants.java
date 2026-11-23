package org.cloudstore.worker.api.constants;

/**
 * Shared worker HTTP paths and header names used by worker API controllers.
 */
public final class WorkerApiConstants
{
    public static final String API_ROOT = "/worker/files";
    public static final String CHUNK_RESERVATION_PATH = "/{fileId}/chunks/{chunkId}/reservation";
    public static final String CHUNK_RESERVATION_CLOSE_PATH = CHUNK_RESERVATION_PATH;
    public static final String RESERVATION_KEY_HEADER = "X-Reservation-Key";
    public static final String CHUNK_PATH = "/{fileId}/chunks/{chunkId}";
    public static final String CHUNK_INVENTORY_PATH = "/chunks";
    public static final String PAGE_QUERY_PARAMETER = "page";
    public static final String PAGE_SIZE_QUERY_PARAMETER = "size";
    public static final String DEFAULT_PAGE_NUMBER = "0";

    private WorkerApiConstants()
    {
    }
}

