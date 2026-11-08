package org.cloudstore.worker.reservation.renewal;

import org.cloudstore.worker.data.ChunkRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Allows a different key to replace an existing key only after its reservation expires.
 */
@Component
@ConditionalOnProperty(
        prefix = "worker.reservation",
        name = "key-renewal-policy",
        havingValue = ReservationRenewalModes.AFTER_EXPIRATION,
        matchIfMissing = true)
public class AfterExpirationKeyRenewalStrategy implements ReservationRenewalStrategy
{
    /**
     * Allows a different key only when the existing reservation has expired.
     *
     * @param existingReservation existing reservation
     * @param currentTime         current time
     * @return {@code true} when its expiration time is not later than the current time
     */
    @Override
    public boolean canAcceptDifferentKey(ChunkRecord existingReservation, Instant currentTime)
    {
        return !existingReservation.getExpiresAt().isAfter(currentTime);
    }
}
