package org.cloudstore.worker.upload.constants;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Tunable settings for upload reservation heartbeat scheduling.
 */
@Component
@ConfigurationProperties(prefix = "worker.upload.heartbeat")
public class WorkerChunkUploadHeartbeatProperties
{
    private long intervalSeconds;
    private int schedulerPoolSize;
    private String threadNamePrefix;

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

    /**
     * Returns the number of shared scheduler threads for upload heartbeats.
     */
    public int getSchedulerPoolSize()
    {
        return schedulerPoolSize;
    }

    /**
     * Sets the number of shared scheduler threads for upload heartbeats.
     */
    public void setSchedulerPoolSize(int schedulerPoolSize)
    {
        this.schedulerPoolSize = schedulerPoolSize;
    }

    /**
     * Returns the name prefix used for heartbeat scheduler threads.
     */
    public String getThreadNamePrefix()
    {
        return threadNamePrefix;
    }

    /**
     * Sets the name prefix used for heartbeat scheduler threads.
     */
    public void setThreadNamePrefix(String threadNamePrefix)
    {
        this.threadNamePrefix = threadNamePrefix;
    }
}
