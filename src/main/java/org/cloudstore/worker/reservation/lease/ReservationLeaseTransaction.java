package org.cloudstore.worker.reservation.lease;

import org.cloudstore.worker.data.reservation.ChunkRecord;
import org.cloudstore.worker.data.reservation.ChunkRecordRepository;
import org.cloudstore.worker.reservation.constants.WorkerReservationProperties;
import org.cloudstore.worker.upload.exceptions.ChunkUploadNotReservedException;
import org.cloudstore.worker.validation.keycomparison.strategies.ReservationKeyComparisonStrategy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Performs reservation lease renewal inside a Spring-proxied transaction.
 */
@Service
public class ReservationLeaseTransaction
{
    private final ChunkRecordRepository chunkRecords;
    private final WorkerReservationProperties reservationProperties;
    private final ReservationKeyComparisonStrategy keyComparisonStrategy;

    /**
     * Creates the database operation with reservation persistence and policy dependencies.
     */
    public ReservationLeaseTransaction(
            ChunkRecordRepository chunkRecords,
            WorkerReservationProperties reservationProperties,
            ReservationKeyComparisonStrategy keyComparisonStrategy)
    {
        this.chunkRecords = chunkRecords;
        this.reservationProperties = reservationProperties;
        this.keyComparisonStrategy = keyComparisonStrategy;
    }

    /**
     * Extends the lease only while the same reservation key is still active.
     * The repository acquires a write lock, serializing this update against competing updates.
     *
     * @throws ChunkUploadNotReservedException when no active reservation matches
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void renewOrThrowIfInactive(String fileId, String chunkId, String key)
    {
        Instant now = Instant.now();
        Optional<ChunkRecord> reservation = chunkRecords.findByFileIdAndChunkId(fileId, chunkId);
        if (reservation.isEmpty()
                || !reservation.get().getExpiresAt().isAfter(now)
                || !keyComparisonStrategy.matches(reservation.get().getReservationKey(), key))
        {
            throw new ChunkUploadNotReservedException();
        }

        reservation.get().renew(key, now.plusSeconds(reservationProperties.getTtlSeconds()));
        chunkRecords.saveAndFlush(reservation.get());
    }
}
