package org.cloudstore.worker;

/**
 * Shared application limits that must remain consistent across components.
 */
public final class Constants
{
    /**
     * Maximum number of characters accepted for a reservation key.
     */
    public static final int MAX_RESERVATION_KEY_LENGTH = 128;

    /** Maximum number of characters accepted for upload and chunk identifiers. */
    public static final int MAX_RESERVATION_ID_LENGTH = 255;

    /** Time a reservation may remain untouched before this worker expires it. */
    public static final long RESERVATION_TIMEOUT_MILLIS = 60_000L;

    /** Delay between reservation cleanup passes. */
    public static final long RESERVATION_CLEANER_INTERVAL_MILLIS = 10_000L;

    /**
     * Format for the message returned when an upload or chunk identifier is invalid.
     */
    public static final String INVALID_RESERVATION_IDS =
            "Upload ID and chunk ID must be non-empty and no longer than %d characters";

    /**
     * Format string for a reservation key length validation message.
     */
    public static final String INVALID_RESERVATION_KEY =
            "Reservation key must be null or contain 1 to %d characters";

    /**
     * Message returned when an identical reservation was already stored.
     */
    public static final String RESERVATION_ALREADY_EXISTS = "Chunk is already reserved";

    /**
     * Message returned when a chunk exists with a different reservation key.
     */
    public static final String RESERVATION_KEY_MISMATCH =
            "Chunk is already reserved with a different key";

    /**
     * Message returned after a reservation is persisted.
     */
    public static final String RESERVATION_CREATED = "Chunk reservation created";

    /**
     * Message returned when a concurrent request conflicts with an existing reservation.
     */
    public static final String RESERVATION_CONFLICT =
            "Reservation conflicts with an existing record";

    /**
     * Prevents construction of this constants-only utility class.
     */
    private Constants()
    {
    }
}
