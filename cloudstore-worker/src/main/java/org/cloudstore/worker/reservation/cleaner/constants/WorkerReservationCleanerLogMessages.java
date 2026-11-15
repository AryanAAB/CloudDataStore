package org.cloudstore.worker.reservation.cleaner.constants;

/**
 * Log templates for reservation expiration cleanup.
 */
public final class WorkerReservationCleanerLogMessages
{
    /**
     * Log template emitted after a cleanup pass.
     */
    public static final String CLEANUP_COMPLETED =
            "Reservation cleanup completed; expiredRecordsRemoved={}";

    /**
     * Prevents construction of this log-template class.
     */
    private WorkerReservationCleanerLogMessages()
    {
    }
}
