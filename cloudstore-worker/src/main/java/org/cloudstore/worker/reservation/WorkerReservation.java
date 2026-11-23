package org.cloudstore.worker.reservation;

import org.cloudstore.worker.data.reservation.ChunkRecord;
import org.cloudstore.worker.data.reservation.ChunkRecordId;
import org.cloudstore.worker.data.reservation.ChunkRecordRepository;
import org.cloudstore.worker.reservation.constants.WorkerReservationLogMessages;
import org.cloudstore.worker.reservation.constants.WorkerReservationMessages;
import org.cloudstore.worker.reservation.constants.WorkerReservationProperties;
import org.cloudstore.worker.reservation.renewal.strategies.ReservationRenewalStrategy;
import org.cloudstore.worker.validation.constants.WorkerValidationProperties;
import org.cloudstore.worker.validation.identifier.IdentifierValidator;
import org.cloudstore.worker.validation.key.ReservationKeyValidator;
import org.cloudstore.worker.validation.keycomparison.strategies.ReservationKeyComparisonStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
    private final ReservationKeyComparisonStrategy keyComparisonStrategy;
    private final WorkerReservationProperties reservationProperties;
    private final WorkerValidationProperties validationProperties;

    /**
     * Creates the reservation operation with its persistence and identifier policy.
     *
     * @param chunkRecords          repository for worker chunk records
     * @param identifierValidator   validation policy for upload and chunk identifiers
     * @param keyValidator          validation policy for optional reservation keys
     * @param renewalStrategy       policy for accepting a different key on an existing reservation
     * @param keyComparisonStrategy policy for deciding whether supplied and stored keys match
     * @param reservationProperties reservation lease settings
     * @param validationProperties  identifier and key length limits
     */
    public WorkerReservation(
            ChunkRecordRepository chunkRecords,
            IdentifierValidator identifierValidator,
            ReservationKeyValidator keyValidator,
            ReservationRenewalStrategy renewalStrategy,
            ReservationKeyComparisonStrategy keyComparisonStrategy,
            WorkerReservationProperties reservationProperties,
            WorkerValidationProperties validationProperties)
    {
        this.chunkRecords = chunkRecords;
        this.identifierValidator = identifierValidator;
        this.keyValidator = keyValidator;
        this.renewalStrategy = renewalStrategy;
        this.keyComparisonStrategy = keyComparisonStrategy;
        this.reservationProperties = reservationProperties;
        this.validationProperties = validationProperties;
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
                            validationProperties.getMaxIdentifierLength()),
                    ReservationResult.FailureCategory.INVALID_REQUEST);
        }
        if (keyValidator.isInvalid(key))
        {
            LOGGER.warn(WorkerReservationLogMessages.INVALID_KEY, fileId, chunkId);
            return ReservationResult.failure(String.format(
                            WorkerReservationMessages.INVALID_KEY,
                            validationProperties.getMaxReservationKeyLength()),
                    ReservationResult.FailureCategory.INVALID_REQUEST);
        }

        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(reservationProperties.getTtlSeconds());
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
     * Removes an active reservation only when its current key matches the caller's key.
     */
    @Transactional
    public boolean close(String chunkId, String fileId, String key)
    {
        if (identifierValidator.isInvalid(fileId)
                || identifierValidator.isInvalid(chunkId)
                || keyValidator.isInvalid(key))
        {
            return false;
        }

        Optional<ChunkRecord> reservation = chunkRecords.findByFileIdAndChunkId(fileId, chunkId);
        if (reservation.isEmpty())
        {
            return true;
        }
        if (!keyComparisonStrategy.matches(reservation.get().getReservationKey(), key))
        {
            return false;
        }
        chunkRecords.delete(reservation.get());
        return true;
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
        boolean keyMatches = keyComparisonStrategy.matches(existing.getReservationKey(), key);
        if (!keyMatches && !renewalStrategy.canAcceptDifferentKey(existing, now))
        {
            LOGGER.warn(WorkerReservationLogMessages.KEY_CONFLICT,
                    existing.getFileId(),
                    existing.getChunkId());
            return ReservationResult.failure(
                    WorkerReservationMessages.KEY_CONFLICT,
                    ReservationResult.FailureCategory.CONFLICT);
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
        return ReservationResult.failure(
                WorkerReservationMessages.CONCURRENT_CONFLICT,
                ReservationResult.FailureCategory.CONFLICT);
    }
}
