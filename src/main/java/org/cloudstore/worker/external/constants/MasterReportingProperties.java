package org.cloudstore.worker.external.constants;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Connection settings identifying this worker to the master reporting API.
 */
@Component
@ConfigurationProperties(prefix = "worker.master")
public class MasterReportingProperties
{
    private String workerId;
    private String corruptionReportUrl;

    public String getWorkerId()
    {
        return workerId;
    }

    public void setWorkerId(String workerId)
    {
        this.workerId = workerId;
    }

    public String getCorruptionReportUrl()
    {
        return corruptionReportUrl;
    }

    public void setCorruptionReportUrl(String corruptionReportUrl)
    {
        this.corruptionReportUrl = corruptionReportUrl;
    }
}
