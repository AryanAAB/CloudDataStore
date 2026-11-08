package org.cloudstore.worker.upload;

import org.cloudstore.worker.upload.constants.WorkerChunkUploadHeartbeatProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Provides scheduler threads for uploads that need periodic reservation renewal.
 */
@Configuration(proxyBeanMethods = false)
public class WorkerChunkUploadHeartbeatConfiguration
{
    /**
     * Creates a shared scheduler so concurrent uploads do not each create a thread.
     */
    @Bean
    public ThreadPoolTaskScheduler workerChunkUploadHeartbeatTaskScheduler(
            WorkerChunkUploadHeartbeatProperties heartbeatProperties)
    {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(heartbeatProperties.getSchedulerPoolSize());
        scheduler.setThreadNamePrefix(heartbeatProperties.getThreadNamePrefix());
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
