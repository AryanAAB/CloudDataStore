package org.cloudstore.worker;

import org.cloudstore.worker.reservation.ReservationResult;
import org.cloudstore.worker.reservation.WorkerReservationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Provides the worker's public entry points for requests from other components.
 */
@Service
public class WorkerFacade
{
    private static final Logger LOGGER = LoggerFactory.getLogger(WorkerFacade.class);

    private final WorkerReservationService reservationService;

    /**
     * Creates the facade with the reservation operation it exposes.
     *
     * @param reservationService service that processes worker reservations
     */
    public WorkerFacade(WorkerReservationService reservationService)
    {
        this.reservationService = reservationService;
    }

    /**
     * Reserves a chunk for an upload by delegating to the reservation service.
     *
     * @param chunkId  identifier assigned to the chunk
     * @param uploadId identifier assigned to the upload
     * @param key      optional reservation key; may be {@code null}
     * @return a result describing whether the reservation succeeded
     */
    public ReservationResult reserve(String chunkId, String uploadId, String key)
    {
        LOGGER.info(Constants.LOG_RESERVATION_RECEIVED);
        ReservationResult result = reservationService.reserve(uploadId, chunkId, key);
        LOGGER.info(Constants.LOG_RESERVATION_COMPLETED, result.success());
        return result;
    }
}
