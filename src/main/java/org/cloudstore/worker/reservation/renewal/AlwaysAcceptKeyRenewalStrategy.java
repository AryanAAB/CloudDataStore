package org.cloudstore.worker.reservation.renewal;

import org.cloudstore.worker.data.ChunkRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Allows a different key to replace an existing reservation key at any time.
 */
@Component
@ConditionalOnProperty(
        prefix = "worker.reservation",
        name = "key-renewal-policy",
        havingValue = ReservationRenewalModes.ALWAYS_ACCEPT)
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
