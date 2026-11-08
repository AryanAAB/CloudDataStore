package org.cloudstore.worker.reservation;

import org.cloudstore.worker.data.ChunkRecord;
import org.cloudstore.worker.data.ChunkRecordId;
import org.cloudstore.worker.data.ChunkRecordRepository;
import org.cloudstore.worker.reservation.constants.WorkerReservationConstants;
import org.cloudstore.worker.reservation.constants.WorkerReservationLogMessages;
import org.cloudstore.worker.reservation.constants.WorkerReservationMessages;
import org.cloudstore.worker.reservation.renewal.ReservationRenewalStrategy;
import org.cloudstore.worker.validation.constants.ValidationConstants;
import org.cloudstore.worker.validation.identifier.IdentifierValidator;
import org.cloudstore.worker.validation.key.ReservationKeyValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Accepts reservation requests and persists expiring reservation records.
 */
@Service
public class WorkerReservation
{
    private static final Logger LOGGER = LoggerFactory.getLogger(WorkerReservation.class);

    private final ChunkRecordRepository chunkRecords;
    private final IdentifierValidator identifierValidator;
    private final ReservationKeyValidator keyValidator;
    private final ReservationRenewalStrategy renewalStrategy;

    /**
     * Creates the reservation operation with its persistence and identifier policy.
     *
     * @param chunkRecords        repository for worker chunk records
     * @param identifierValidator validation policy for upload and chunk identifiers
     * @param keyValidator        validation policy for optional reservation keys
     * @param renewalStrategy     policy for accepting a different key on an existing reservation
     */
    public WorkerReservation(
            ChunkRecordRepository chunkRecords,
            IdentifierValidator identifierValidator,
            ReservationKeyValidator keyValidator,
            ReservationRenewalStrategy renewalStrategy)
    {
        this.chunkRecords = chunkRecords;
        this.identifierValidator = identifierValidator;
        this.keyValidator = keyValidator;
        this.renewalStrategy = renewalStrategy;
    }

    /**
     * Creates or renews a reservation identified by its upload and chunk IDs.
     * A matching retry extends the lease. A different key conflicts while the
     * existing lease is active; an expired record can be reused.
     *
     * @param chunkId chunk identifier
     * @param fileId  file identifier
     * @param key     optional reservation key; may be {@code null}
     * @return success or failure details
     */
    public ReservationResult reserve(String chunkId, String fileId, String key)
    {
        LOGGER.info(WorkerReservationLogMessages.REQUEST_RECEIVED);
        if (identifierValidator.isInvalid(fileId) || identifierValidator.isInvalid(chunkId))
        {
            LOGGER.warn(WorkerReservationLogMessages.INVALID_IDENTIFIERS);
            return ReservationResult.failure(String.format(
                    WorkerReservationMessages.INVALID_IDENTIFIERS,
                    ValidationConstants.MAX_IDENTIFIER_LENGTH));
        }
        if (keyValidator.isInvalid(key))
        {
            LOGGER.warn(WorkerReservationLogMessages.INVALID_KEY, fileId, chunkId);
            return ReservationResult.failure(String.format(
                    WorkerReservationMessages.INVALID_KEY,
                    ValidationConstants.MAX_RESERVATION_KEY_LENGTH));
        }

        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(WorkerReservationConstants.RESERVATION_TTL_MILLIS);
        ChunkRecordId id = new ChunkRecordId(fileId, chunkId);
        Optional<ChunkRecord> existing = chunkRecords.findById(id);
        if (existing.isPresent())
        {
            return renewExisting(existing.get(), key, now, expiresAt);
        }

        ChunkRecord reservation = new ChunkRecord(fileId, chunkId, key, expiresAt);
        try
        {
            chunkRecords.saveAndFlush(reservation);
            LOGGER.info(WorkerReservationLogMessages.CREATED, fileId, chunkId);
            return ReservationResult.success(WorkerReservationMessages.CREATED);
        } catch (DataIntegrityViolationException concurrentInsert)
        {
            return resolveConcurrentInsert(id, key, now, expiresAt);
        }
    }

    /**
     * Renews an active matching reservation or reuses an expired record.
     */
    private ReservationResult renewExisting(
            ChunkRecord existing,
            String key,
            Instant now,
            Instant expiresAt)
    {
        boolean keyMatches = Objects.equals(existing.getReservationKey(), key);
        if (!keyMatches && !renewalStrategy.canAcceptDifferentKey(existing, now))
        {
            LOGGER.warn(WorkerReservationLogMessages.KEY_CONFLICT,
                    existing.getFileId(),
                    existing.getChunkId());
            return ReservationResult.failure(WorkerReservationMessages.KEY_CONFLICT);
        }

        existing.renew(key, expiresAt);
        chunkRecords.saveAndFlush(existing);
        LOGGER.info(WorkerReservationLogMessages.RENEWED,
                existing.getFileId(),
                existing.getChunkId());
        return ReservationResult.success(WorkerReservationMessages.RENEWED);
    }

    /**
     * Resolves the result when another request inserted the same composite ID first.
     */
    private ReservationResult resolveConcurrentInsert(
            ChunkRecordId id,
            String key,
            Instant now,
            Instant expiresAt)
    {
        Optional<ChunkRecord> winner = chunkRecords.findById(id);
        if (winner.isPresent())
        {
            return renewExisting(winner.get(), key, now, expiresAt);
        }

        LOGGER.warn(WorkerReservationLogMessages.CONCURRENT_CONFLICT,
                id.getFileId(),
                id.getChunkId());
        return ReservationResult.failure(WorkerReservationMessages.CONCURRENT_CONFLICT);
    }
}
