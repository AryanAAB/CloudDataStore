package org.cloudstore.worker.integrity.constants;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Settings for scheduled chunk integrity scans.
 */
@Component
@ConfigurationProperties(prefix = "worker.integrity.scan")
public class IntegrityScanProperties
{
    private long intervalSeconds = 300L;
    private int batchSize = 50;

    public long getIntervalSeconds()
    {
        return intervalSeconds;
    }

    public void setIntervalSeconds(long intervalSeconds)
    {
        this.intervalSeconds = intervalSeconds;
    }

    public int getBatchSize()
    {
        return batchSize;
    }

    public void setBatchSize(int batchSize)
    {
        this.batchSize = batchSize;
    }
}
