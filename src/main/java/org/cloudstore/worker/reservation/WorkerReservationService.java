package org.cloudstore.worker.reservation;

import org.cloudstore.common.Constants;
import org.cloudstore.common.ChunkState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Handles reservation requests received by a storage worker. */
@Service
public class WorkerReservationService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(WorkerReservationService.class);

    private final ChunkReservationRepository reservations;

    /**
     * Creates the service with the repository used to persist reservations.
     *
     * @param reservations repository for reservation records
     */
    public WorkerReservationService(ChunkReservationRepository reservations)
    {
        this.reservations = reservations;
    }

    /**
     * Reserves a chunk for an upload. Repeating the same request succeeds
     * without creating another row; conflicting IDs or keys return a failure.
     *
     * @param uploadId UUID assigned to the upload
     * @param chunkId UUID assigned to the chunk
     * @param key optional reservation key supplied by the master; may be {@code null}
     * @return a result describing whether the reservation was created or already existed
     */
    public ReservationResult reserve(String uploadId, String chunkId, String key)
    {
        String normalizedUploadId = normalizeUuid(uploadId);
        String normalizedChunkId = normalizeUuid(chunkId);
        if (normalizedUploadId == null || normalizedChunkId == null)
        {
            LOGGER.warn(Constants.LOG_RESERVATION_INVALID_IDS);
            return ReservationResult.failure(Constants.INVALID_RESERVATION_IDS);
        }
        if (key != null
                && (key.isBlank() || key.length() > Constants.MAX_RESERVATION_KEY_LENGTH))
        {
            LOGGER.warn(Constants.LOG_RESERVATION_INVALID_KEY,
                    normalizedUploadId,
                    normalizedChunkId);
            return ReservationResult.failure(String.format(
                    Constants.INVALID_RESERVATION_KEY,
                    Constants.MAX_RESERVATION_KEY_LENGTH));
        }

        ChunkReservationId id = new ChunkReservationId(normalizedUploadId, normalizedChunkId);
        Optional<ChunkReservation> existing = reservations.findById(id);
        if (existing.isPresent())
        {
            if (Objects.equals(existing.get().getReservationKey(), key))
            {
                LOGGER.info(Constants.LOG_RESERVATION_ALREADY_EXISTS,
                        normalizedUploadId,
                        normalizedChunkId);
                return ReservationResult.success(Constants.RESERVATION_ALREADY_EXISTS);
            }
            LOGGER.warn(Constants.LOG_RESERVATION_KEY_MISMATCH,
                    normalizedUploadId,
                    normalizedChunkId);
            return ReservationResult.failure(Constants.RESERVATION_KEY_MISMATCH);
        }
        ChunkReservation reservation = new ChunkReservation(
                normalizedUploadId,
                normalizedChunkId,
                key,
                Instant.now(),
                ChunkState.RESERVED);
        try
        {
            reservations.saveAndFlush(reservation);
            LOGGER.info(Constants.LOG_RESERVATION_CREATED,
                    normalizedUploadId,
                    normalizedChunkId);
            return ReservationResult.success(Constants.RESERVATION_CREATED);
        } catch (DataIntegrityViolationException concurrentDuplicate)
        {
            // The database's composite primary key protects concurrent reservations.
            LOGGER.warn(Constants.LOG_RESERVATION_CONCURRENT_CONFLICT,
                    normalizedUploadId,
                    normalizedChunkId);
            Optional<ChunkReservation> winner = reservations.findById(id);
            if (winner.isPresent() && Objects.equals(winner.get().getReservationKey(), key))
            {
                LOGGER.info(Constants.LOG_RESERVATION_CONCURRENT_RETRY,
                        normalizedUploadId,
                        normalizedChunkId);
                return ReservationResult.success(Constants.RESERVATION_ALREADY_EXISTS);
            }
            return ReservationResult.failure(Constants.RESERVATION_CONFLICT);
        }
    }

    /** Converts a UUID string to its canonical form, or returns {@code null} if invalid. */
    private static String normalizeUuid(String value)
    {
        if (value == null)
        {
            return null;
        }
        try
        {
            return UUID.fromString(value).toString();
        } catch (IllegalArgumentException invalidUuid)
        {
            return null;
        }
    }
}
