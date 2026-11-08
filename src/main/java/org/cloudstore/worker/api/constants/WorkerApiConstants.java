package org.cloudstore.worker.api.constants;

/**
 * Shared worker HTTP paths and header names used by worker API controllers.
 */
public final class WorkerApiConstants
{
    public static final String API_ROOT = "/worker/files";
    public static final String CHUNK_RESERVATION_PATH = "/{fileId}/chunks/{chunkId}/reservation";
    public static final String RESERVATION_KEY_HEADER = "X-Reservation-Key";

    private WorkerApiConstants()
    {
    }
}
