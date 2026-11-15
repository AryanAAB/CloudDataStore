package org.cloudstore.worker.heartbeat.constants;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the shared worker heartbeat scheduler.
 */
@ConfigurationProperties(prefix = "worker.heartbeat")
public class HeartbeatProperties
{
    private int schedulerPoolSize;
    private String threadNamePrefix;

    /**
     * Returns the number of shared heartbeat scheduler threads.
     */
    public int getSchedulerPoolSize()
    {
        return schedulerPoolSize;
    }

    /**
     * Sets the number of shared heartbeat scheduler threads.
     */
    public void setSchedulerPoolSize(int schedulerPoolSize)
    {
        this.schedulerPoolSize = schedulerPoolSize;
    }

    /**
     * Returns the name prefix for heartbeat scheduler threads.
     */
    public String getThreadNamePrefix()
    {
        return threadNamePrefix;
    }

    /**
     * Sets the name prefix for heartbeat scheduler threads.
     */
    public void setThreadNamePrefix(String threadNamePrefix)
    {
        this.threadNamePrefix = threadNamePrefix;
    }
}
