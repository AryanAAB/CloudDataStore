package org.cloudstore.worker.integrity;

import org.cloudstore.worker.integrity.constants.IntegrityScanProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Duration;

/**
 * Schedules periodic bounded chunk integrity scans.
 */
@Configuration(proxyBeanMethods = false)
public class WorkerChunkIntegrityScannerScheduling implements SchedulingConfigurer
{
    private final WorkerChunkIntegrityScanner scanner;
    private final IntegrityScanProperties properties;

    public WorkerChunkIntegrityScannerScheduling(
            WorkerChunkIntegrityScanner scanner,
            IntegrityScanProperties properties)
    {
        this.scanner = scanner;
        this.properties = properties;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar)
    {
        taskRegistrar.addFixedDelayTask(scanner::scanBatch,
                Duration.ofSeconds(properties.getIntervalSeconds()));
    }
}
