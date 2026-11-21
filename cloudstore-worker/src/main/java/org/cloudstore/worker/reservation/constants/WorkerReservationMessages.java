package org.cloudstore.worker.reservation.constants;

/**
 * User-facing messages returned by worker reservation operations.
 */
public final class WorkerReservationMessages
{
    /**
     * Format for invalid file or chunk identifiers.
     */
    public static final String INVALID_IDENTIFIERS =
            "File ID and chunk ID must be non-empty and no longer than %d characters";

    /**
     * Format for an invalid non-null reservation key.
     */
    public static final String INVALID_KEY =
            "Reservation key must be non-blank and no longer than %d characters";

    /**
     * Message returned after creating a new reservation.
     */
    public static final String CREATED = "Reservation created";

    /**
     * Message returned after renewing a matching reservation.
     */
    public static final String RENEWED = "Matching reservation renewed";

    /**
     * Message returned when the active reservation key does not match.
     */
    public static final String KEY_CONFLICT = "An active reservation exists with a different key";

    /**
     * Message returned when persistence detects a conflicting concurrent request.
     */
    public static final String CONCURRENT_CONFLICT =
            "Reservation conflicts with a concurrent request";

    /**
     * Prevents construction of this message-only class.
     */
    private WorkerReservationMessages()
    {
    }
}
