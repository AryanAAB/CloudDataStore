package org.cloudstore.worker.reservation.cleaner.constants;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Runtime settings for periodic reservation cleanup.
 */
@Component
@ConfigurationProperties(prefix = "worker.reservation.cleaner")
public class WorkerReservationCleanerProperties
{
    private long intervalSeconds;

    /**
     * Returns the delay between cleanup passes, in seconds.
     */
    public long getIntervalSeconds()
    {
        return intervalSeconds;
    }

    /**
     * Sets the delay between cleanup passes, in seconds.
     */
    public void setIntervalSeconds(long intervalSeconds)
    {
        this.intervalSeconds = intervalSeconds;
    }
}
