package org.cloudstore.worker.upload;

import org.cloudstore.worker.data.reservation.ChunkRecord;
import org.cloudstore.worker.data.reservation.ChunkRecordRepository;
import org.cloudstore.worker.reservation.constants.WorkerReservationProperties;
import org.cloudstore.worker.upload.constants.WorkerChunkUploadHeartbeatProperties;
import org.cloudstore.worker.upload.exceptions.ChunkUploadNotReservedException;
import org.cloudstore.worker.validation.keycomparison.strategies.ReservationKeyComparisonStrategy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Renews active upload reservations in short, independent database transactions.
 */
@Service
public class WorkerReservationLeaseHeartbeat
{
    private final ChunkRecordRepository chunkRecords;
    private final WorkerReservationProperties reservationProperties;
    private final WorkerChunkUploadHeartbeatProperties heartbeatProperties;
    private final ReservationKeyComparisonStrategy keyComparisonStrategy;

    /**
     * Creates the heartbeat operation with reservation persistence and lease settings.
     */
    public WorkerReservationLeaseHeartbeat(
            ChunkRecordRepository chunkRecords,
            WorkerReservationProperties reservationProperties,
            WorkerChunkUploadHeartbeatProperties heartbeatProperties,
            ReservationKeyComparisonStrategy keyComparisonStrategy)
    {
        this.chunkRecords = chunkRecords;
        this.reservationProperties = reservationProperties;
        this.heartbeatProperties = heartbeatProperties;
        this.keyComparisonStrategy = keyComparisonStrategy;
    }

    /**
     * Extends the lease only while the same reservation key is still active.
     * The row lock serializes this update against the cleaner's expiry deletion.
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

    /**
     * Returns the configured heartbeat interval, capped at one third of the reservation lease.
     */
    public Duration getHeartbeatInterval()
    {
        Duration leaseSafeInterval = Duration.ofSeconds(reservationProperties.getTtlSeconds())
                .dividedBy(3L);
        Duration configuredInterval = Duration.ofSeconds(heartbeatProperties.getIntervalSeconds());
        return configuredInterval.compareTo(leaseSafeInterval) < 0
                ? configuredInterval
                : leaseSafeInterval;
    }
}
