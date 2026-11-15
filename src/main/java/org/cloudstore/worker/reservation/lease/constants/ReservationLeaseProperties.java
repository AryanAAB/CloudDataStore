package org.cloudstore.worker.reservation.lease.constants;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Tunable settings for reservation lease heartbeat scheduling.
 */
@Component
@ConfigurationProperties(prefix = "worker.reservation.heartbeat")
public class ReservationLeaseProperties
{
    private long intervalSeconds;

    /**
     * Returns the maximum configured delay between lease heartbeat attempts.
     */
    public long getIntervalSeconds()
    {
        return intervalSeconds;
    }

    /**
     * Sets the maximum configured delay between lease heartbeat attempts.
     */
    public void setIntervalSeconds(long intervalSeconds)
    {
        this.intervalSeconds = intervalSeconds;
    }

}
