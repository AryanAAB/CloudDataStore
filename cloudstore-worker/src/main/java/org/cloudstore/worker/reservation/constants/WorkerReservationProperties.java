package org.cloudstore.worker.reservation.constants;

import org.cloudstore.worker.reservation.renewal.ReservationRenewalModes;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Runtime settings for worker reservation leases.
 */
@Component
@ConfigurationProperties(prefix = "worker.reservation")
public class WorkerReservationProperties
{
    private long ttlSeconds;
    private ReservationRenewalModes keyRenewalPolicy = ReservationRenewalModes.AFTER_EXPIRATION;

    /**
     * Returns the reservation lease length in seconds.
     */
    public long getTtlSeconds()
    {
        return ttlSeconds;
    }

    /**
     * Sets the reservation lease length in seconds.
     */
    public void setTtlSeconds(long ttlSeconds)
    {
        this.ttlSeconds = ttlSeconds;
    }

    /**
     * Returns the policy used when a reservation key changes.
     */
    public ReservationRenewalModes getKeyRenewalPolicy()
    {
        return keyRenewalPolicy;
    }

    /**
     * Sets the policy used when a reservation key changes.
     */
    public void setKeyRenewalPolicy(ReservationRenewalModes keyRenewalPolicy)
    {
        this.keyRenewalPolicy = keyRenewalPolicy;
    }

}
