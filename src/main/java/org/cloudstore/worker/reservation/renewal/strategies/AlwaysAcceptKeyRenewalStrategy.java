package org.cloudstore.worker.reservation.renewal.strategies;

import org.cloudstore.worker.data.ChunkRecord;

import java.time.Instant;

/**
 * Allows a different key to replace an existing reservation key at any time.
 */
public class AlwaysAcceptKeyRenewalStrategy implements ReservationRenewalStrategy
{
    /**
     * Always allows replacement of the current key.
     *
     * @param existingReservation existing reservation
     * @param currentTime         current time
     * @return always {@code true}
     */
    @Override
    public boolean canAcceptDifferentKey(ChunkRecord existingReservation, Instant currentTime)
    {
        return true;
    }
}
