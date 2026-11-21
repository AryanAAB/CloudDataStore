package org.cloudstore.worker.external;

import org.cloudstore.worker.external.constants.MasterReportingConstants;
import org.cloudstore.worker.external.constants.MasterReportingProperties;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;

/**
 * Registers this worker with the master and periodically reports its availability.
 */
@Component
public class MasterWorkerLifecycleClient implements ApplicationRunner, SchedulingConfigurer
{
    private static final Logger LOGGER = LoggerFactory.getLogger(MasterWorkerLifecycleClient.class);

    private final RestClient restClient = RestClient.create();
    private final MasterReportingProperties properties;
    private volatile boolean registered;

    public MasterWorkerLifecycleClient(MasterReportingProperties properties)
    {
        this.properties = properties;
    }

    @Override
    public void run(@NonNull ApplicationArguments args)
    {
        registerWorker();
    }

    @Override
    public void configureTasks(@NonNull ScheduledTaskRegistrar taskRegistrar)
    {
        taskRegistrar.addFixedDelayTask(this::sendHeartbeat,
                Duration.ofSeconds(properties.getHeartbeatIntervalSeconds()));
    }

    private void sendHeartbeat()
    {
        if (!registered)
        {
            registerWorker();
            return;
        }
        try
        {
            restClient.post()
                    .uri(properties.getHeartbeatUrl())
                    .body(new MasterWorkerHeartbeat(
                            properties.getWorkerId(), MasterReportingConstants.WORKER_AVAILABLE, Instant.now()))
                    .retrieve()
                    .toBodilessEntity();
            LOGGER.info(MasterReportingConstants.WORKER_HEARTBEAT_SENT, properties.getWorkerId());
        } catch (RuntimeException heartbeatFailure)
        {
            registered = false;
            LOGGER.error(MasterReportingConstants.WORKER_HEARTBEAT_FAILED,
                    properties.getWorkerId(), heartbeatFailure);
        }
    }

    private void registerWorker()
    {
        try
        {
            restClient.post()
                    .uri(properties.getRegistrationUrl())
                    .body(new MasterWorkerRegistration(
                            properties.getWorkerId(), properties.getAdvertisedAddress()))
                    .retrieve()
                    .toBodilessEntity();
            registered = true;
            LOGGER.info(MasterReportingConstants.WORKER_REGISTERED, properties.getWorkerId());
        } catch (RuntimeException registrationFailure)
        {
            registered = false;
            LOGGER.error(MasterReportingConstants.WORKER_REGISTRATION_FAILED,
                    properties.getWorkerId(), registrationFailure);
        }
    }
}
