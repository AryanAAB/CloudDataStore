package org.cloudstore.worker.reservation.constants;

/**
 * Centralized log templates for worker reservation operations.
 */
public final class WorkerReservationLogMessages
{
    /**
     * Log template emitted when a reservation request is received.
     */
    public static final String REQUEST_RECEIVED = "Received worker reservation request";

    /**
     * Log template emitted when file or chunk identifiers fail validation.
     */
    public static final String INVALID_IDENTIFIERS =
            "Reservation rejected because a file ID or chunk ID is invalid";

    /**
     * Log template emitted when the reservation key fails validation.
     */
    public static final String INVALID_KEY =
            "Reservation rejected for file {} and chunk {} because the key is invalid";

    /**
     * Log template emitted after creating a reservation.
     */
    public static final String CREATED = "Created reservation for file {} and chunk {}";

    /**
     * Log template emitted after renewing a matching reservation.
     */
    public static final String RENEWED = "Renewed reservation for file {} and chunk {}";

    /**
     * Log template emitted when the active reservation key does not match.
     */
    public static final String KEY_CONFLICT =
            "Reservation key conflict for file {} and chunk {}";

    /**
     * Log template emitted when persistence detects a concurrent conflict.
     */
    public static final String CONCURRENT_CONFLICT =
            "Concurrent reservation conflict for file {} and chunk {}";

    /**
     * Prevents construction of this log-template class.
     */
    private WorkerReservationLogMessages()
    {
    }
}
