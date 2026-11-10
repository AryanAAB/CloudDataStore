package org.cloudstore.worker.heartbeat;

import org.cloudstore.worker.heartbeat.constants.HeartbeatProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Configures the shared scheduler used by worker heartbeats.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(HeartbeatProperties.class)
public class HeartbeatConfiguration
{
    /**
     * Creates the shared scheduler so each long-running operation does not create its own thread.
     */
    @Bean
    public ThreadPoolTaskScheduler workerHeartbeatTaskScheduler(HeartbeatProperties properties)
    {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(properties.getSchedulerPoolSize());
        scheduler.setThreadNamePrefix(properties.getThreadNamePrefix());
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
