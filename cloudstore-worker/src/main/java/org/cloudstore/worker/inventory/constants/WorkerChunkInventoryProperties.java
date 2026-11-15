package org.cloudstore.worker.inventory.constants;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Settings that bound pages returned by the worker chunk inventory endpoint.
 */
@Component
@ConfigurationProperties(prefix = "worker.inventory")
public class WorkerChunkInventoryProperties
{
    private int defaultPageSize;
    private int maximumPageSize;

    /**
     * Returns the configured page size when the caller omits one.
     */
    public int getDefaultPageSize()
    {
        return defaultPageSize;
    }

    /**
     * Sets the page size used when the caller omits one.
     */
    public void setDefaultPageSize(int defaultPageSize)
    {
        this.defaultPageSize = defaultPageSize;
    }

    /**
     * Returns the largest page size this worker will serve.
     */
    public int getMaximumPageSize()
    {
        return maximumPageSize;
    }

    /**
     * Sets the largest page size this worker will serve.
     */
    public void setMaximumPageSize(int maximumPageSize)
    {
        this.maximumPageSize = maximumPageSize;
    }
}
