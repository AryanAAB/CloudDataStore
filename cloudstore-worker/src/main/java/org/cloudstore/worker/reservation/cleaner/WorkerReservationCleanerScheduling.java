package org.cloudstore.worker.reservation.cleaner;

import org.cloudstore.worker.reservation.cleaner.constants.WorkerReservationCleanerProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Duration;

/**
 * Registers reservation cleanup using the bound worker reservation settings.
 */
@Configuration(proxyBeanMethods = false)
public class WorkerReservationCleanerScheduling implements SchedulingConfigurer
{
    private final WorkerReservationCleaner cleaner;
    private final WorkerReservationCleanerProperties cleanerProperties;

    /**
     * Creates the scheduler with the cleaner and its bound configuration.
     */
    public WorkerReservationCleanerScheduling(
            WorkerReservationCleaner cleaner,
            WorkerReservationCleanerProperties cleanerProperties)
    {
        this.cleaner = cleaner;
        this.cleanerProperties = cleanerProperties;
    }

    /**
     * Registers cleanup at the configured fixed delay.
     */
    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar)
    {
        Duration cleanupInterval = Duration.ofSeconds(cleanerProperties.getIntervalSeconds());
        taskRegistrar.addFixedDelayTask(cleaner::cleanExpiredRecords, cleanupInterval);
    }
}
