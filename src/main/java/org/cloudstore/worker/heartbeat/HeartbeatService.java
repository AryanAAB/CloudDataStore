package org.cloudstore.worker.heartbeat;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;

/**
 * Schedules recurring heartbeats for long-running worker operations.
 */
@Service
public class HeartbeatService
{
    private final TaskScheduler taskScheduler;

    /**
     * Creates the scheduler-backed heartbeat service.
     */
    public HeartbeatService(
            @Qualifier("workerHeartbeatTaskScheduler") TaskScheduler taskScheduler)
    {
        this.taskScheduler = taskScheduler;
    }

    /**
     * Starts a heartbeat at a fixed interval and returns a handle to stop it.
     */
    public HeartbeatHandle start(Runnable heartbeat, Duration interval)
    {
        ScheduledFuture<?> scheduledHeartbeat = taskScheduler.scheduleAtFixedRate(heartbeat, interval);
        return new HeartbeatHandle(scheduledHeartbeat);
    }

    /**
     * Handle for stopping a scheduled heartbeat.
     */
    public static class HeartbeatHandle implements AutoCloseable
    {
        private final ScheduledFuture<?> scheduledHeartbeat;

        private HeartbeatHandle(ScheduledFuture<?> scheduledHeartbeat)
        {
            this.scheduledHeartbeat = scheduledHeartbeat;
        }

        /**
         * Cancels future heartbeat executions without interrupting one already running.
         */
        @Override
        public void close()
        {
            scheduledHeartbeat.cancel(false);
        }
    }
}
