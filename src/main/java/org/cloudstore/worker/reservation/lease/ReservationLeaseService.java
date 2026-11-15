package org.cloudstore.worker.reservation.lease;

import org.cloudstore.worker.heartbeat.HeartbeatService;
import org.cloudstore.worker.reservation.constants.WorkerReservationProperties;
import org.cloudstore.worker.reservation.lease.constants.ReservationLeaseProperties;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Renews active reservations in short, independent database transactions for long-running operations.
 */
@Service
public class ReservationLeaseService
{
    private final ReservationLeaseTransaction leaseTransaction;
    private final WorkerReservationProperties reservationProperties;
    private final ReservationLeaseProperties leaseProperties;
    private final HeartbeatService heartbeatService;

    /**
     * Creates the heartbeat operation with reservation persistence and lease settings.
     */
    public ReservationLeaseService(
            ReservationLeaseTransaction leaseTransaction,
            WorkerReservationProperties reservationProperties,
            ReservationLeaseProperties leaseProperties,
            HeartbeatService heartbeatService)
    {
        this.leaseTransaction = leaseTransaction;
        this.reservationProperties = reservationProperties;
        this.leaseProperties = leaseProperties;
        this.heartbeatService = heartbeatService;
    }

    /**
     * Extends the lease only while the same reservation key is still active.
     */
    public void renewOrThrowIfInactive(String fileId, String chunkId, String key)
    {
        leaseTransaction.renewOrThrowIfInactive(fileId, chunkId, key);
    }

    /**
     * Returns the configured heartbeat interval, capped at one third of the reservation lease.
     */
    public Duration getHeartbeatInterval()
    {
        Duration leaseSafeInterval = Duration.ofSeconds(reservationProperties.getTtlSeconds())
                .dividedBy(3L);
        Duration configuredInterval = Duration.ofSeconds(leaseProperties.getIntervalSeconds());
        return configuredInterval.compareTo(leaseSafeInterval) < 0
                ? configuredInterval
                : leaseSafeInterval;
    }

    /**
     * Renews immediately, then periodically until the returned handle is closed.
     */
    public LeaseRenewalHandle startRenewal(
            String fileId, String chunkId, String key, Runnable onLeaseLost)
    {
        renewOrThrowIfInactive(fileId, chunkId, key);
        AtomicBoolean leaseLost = new AtomicBoolean(false);
        HeartbeatService.HeartbeatHandle heartbeat = heartbeatService.start(() ->
        {
            if (leaseLost.get())
            {
                return;
            }
            try
            {
                renewOrThrowIfInactive(fileId, chunkId, key);
            } catch (RuntimeException renewalFailure)
            {
                if (leaseLost.compareAndSet(false, true))
                {
                    onLeaseLost.run();
                }
            }
        }, getHeartbeatInterval());
        return new LeaseRenewalHandle(heartbeat, leaseLost);
    }

    /**
     * Handle that exposes lease-loss state and stops periodic renewal on close.
     */
    public static class LeaseRenewalHandle implements AutoCloseable
    {
        private final HeartbeatService.HeartbeatHandle heartbeat;
        private final AtomicBoolean leaseLost;

        private LeaseRenewalHandle(HeartbeatService.HeartbeatHandle heartbeat, AtomicBoolean leaseLost)
        {
            this.heartbeat = heartbeat;
            this.leaseLost = leaseLost;
        }

        /**
         * Indicates whether a scheduled renewal failed.
         */
        public boolean isLeaseLost()
        {
            return leaseLost.get();
        }

        @Override
        public void close()
        {
            heartbeat.close();
        }
    }
}
