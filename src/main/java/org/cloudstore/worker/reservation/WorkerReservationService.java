package org.cloudstore.worker.reservation;

import org.cloudstore.worker.ChunkState;
import org.cloudstore.worker.Constants;
import org.cloudstore.worker.key.ReservationIdentifierValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Handles reservation requests received by a storage worker.
 */
@Service
public class WorkerReservationService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(WorkerReservationService.class);

    private final ChunkReservationRepository reservations;
    private final ReservationIdentifierValidator identifierValidator;

    /**
     * Creates the service with the repository used to persist reservations.
     *
     * @param reservations        repository for reservation records
     * @param identifierValidator policy used to validate upload and chunk identifiers
     */
    public WorkerReservationService(
            ChunkReservationRepository reservations,
            ReservationIdentifierValidator identifierValidator)
    {
        this.reservations = reservations;
        this.identifierValidator = identifierValidator;
    }

    /**
     * Reserves a chunk for an upload. Repeating the same request succeeds
     * without creating another row; conflicting IDs or keys return a failure.
     *
     * @param uploadId identifier assigned to the upload
     * @param chunkId  identifier assigned to the chunk
     * @param key      optional reservation key supplied by the master; may be {@code null}
     * @return a result describing whether the reservation was created or already existed
     */
    public ReservationResult reserve(String uploadId, String chunkId, String key)
    {
        if (!identifierValidator.isValid(uploadId) || !identifierValidator.isValid(chunkId))
        {
            LOGGER.warn(ReservationLogMessages.Processing.INVALID_IDENTIFIERS);
            return ReservationResult.failure(String.format(
                    Constants.INVALID_RESERVATION_IDS,
                    Constants.MAX_RESERVATION_ID_LENGTH));
        }
        if (key != null
                && (key.isBlank() || key.length() > Constants.MAX_RESERVATION_KEY_LENGTH))
        {
            LOGGER.warn(ReservationLogMessages.Processing.INVALID_KEY,
                    uploadId,
                    chunkId);
            return ReservationResult.failure(String.format(
                    Constants.INVALID_RESERVATION_KEY,
                    Constants.MAX_RESERVATION_KEY_LENGTH));
        }

        ChunkReservationId id = new ChunkReservationId(uploadId, chunkId);
        Optional<ChunkReservation> existing = reservations.findById(id);
        if (existing.isPresent())
        {
            if (Objects.equals(existing.get().getReservationKey(), key))
            {
                LOGGER.info(ReservationLogMessages.Processing.ALREADY_EXISTS,
                        uploadId,
                        chunkId);
                return ReservationResult.success(Constants.RESERVATION_ALREADY_EXISTS);
            }
            LOGGER.warn(ReservationLogMessages.Processing.KEY_MISMATCH,
                    uploadId,
                    chunkId);
            return ReservationResult.failure(Constants.RESERVATION_KEY_MISMATCH);
        }
        ChunkReservation reservation = new ChunkReservation(
                uploadId,
                chunkId,
                key,
                Instant.now(),
                ChunkState.RESERVED);
        try
        {
            reservations.saveAndFlush(reservation);
            LOGGER.info(ReservationLogMessages.Processing.CREATED,
                    uploadId,
                    chunkId);
            return ReservationResult.success(Constants.RESERVATION_CREATED);
        } catch (DataIntegrityViolationException concurrentDuplicate)
        {
            // The database's composite primary key protects concurrent reservations.
            LOGGER.warn(ReservationLogMessages.Processing.CONCURRENT_CONFLICT,
                    uploadId,
                    chunkId);
            Optional<ChunkReservation> winner = reservations.findById(id);
            if (winner.isPresent() && Objects.equals(winner.get().getReservationKey(), key))
            {
                LOGGER.info(ReservationLogMessages.Processing.CONCURRENT_RETRY,
                        uploadId,
                        chunkId);
                return ReservationResult.success(Constants.RESERVATION_ALREADY_EXISTS);
            }
            return ReservationResult.failure(Constants.RESERVATION_CONFLICT);
        }
    }

}
