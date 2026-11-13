package org.cloudstore.worker.external;

import org.cloudstore.worker.external.constants.MasterReportingConstants;
import org.cloudstore.worker.external.constants.MasterReportingProperties;
import org.cloudstore.worker.integrity.ChunkCorruptionReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Sends confirmed chunk corruption findings to the configured master endpoint.
 */
@Component
public class MasterChunkCorruptionReporter implements ChunkCorruptionReporter
{
    private static final Logger LOGGER = LoggerFactory.getLogger(MasterChunkCorruptionReporter.class);

    private final RestClient restClient;
    private final MasterReportingProperties properties;

    public MasterChunkCorruptionReporter(MasterReportingProperties properties)
    {
        this.restClient = RestClient.create();
        this.properties = properties;
    }

    @Override
    public void report(String fileId, String chunkId, String reason)
    {
        try
        {
            restClient.post()
                    .uri(properties.getCorruptionReportUrl(), properties.getWorkerId())
                    .body(new MasterChunkCorruptionReport(fileId, chunkId, reason))
                    .retrieve()
                    .toBodilessEntity();
            LOGGER.info(MasterReportingConstants.CORRUPTION_REPORT_SUBMITTED, fileId, chunkId);
        } catch (RuntimeException reportingFailure)
        {
            LOGGER.error(MasterReportingConstants.CORRUPTION_REPORT_FAILED,
                    fileId, chunkId, reportingFailure);
            throw reportingFailure;
        }
    }
}
