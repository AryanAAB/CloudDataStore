package org.cloudstore.worker.reservation.renewal;

import org.cloudstore.worker.data.ChunkRecord;

import java.time.Instant;

/**
 * Defines when a new key may replace the key on an existing reservation.
 */
public interface ReservationRenewalStrategy
{
    /**
     * Determines whether the supplied reservation may be renewed with a different key.
     *
     * @param existingReservation reservation currently stored for the chunk
     * @param currentTime         time used to determine whether the reservation has expired
     * @return {@code true} when the different key may be accepted
     */
    boolean canAcceptDifferentKey(ChunkRecord existingReservation, Instant currentTime);
}
