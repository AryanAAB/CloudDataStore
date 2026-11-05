package org.cloudstore.common;

/**
 * Shared application limits that must remain consistent across components.
 */
public final class Constants
{
    /**
     * Maximum number of characters accepted for a reservation key.
     */
    public static final int MAX_RESERVATION_KEY_LENGTH = 128;

    /**
     * Message returned when either supplied identifier is not a UUID.
     */
    public static final String INVALID_RESERVATION_IDS =
            "Upload ID and chunk ID must be valid UUIDs";

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

    /** Log template emitted when the facade receives a reservation request. */
    public static final String LOG_RESERVATION_RECEIVED = "Received reserve operation";

    /** Log template emitted when the facade completes a reservation request. */
    public static final String LOG_RESERVATION_COMPLETED = "Reserve operation completed: success={}";

    /** Log template emitted when reservation identifiers fail validation. */
    public static final String LOG_RESERVATION_INVALID_IDS =
            "Reservation rejected because upload ID or chunk ID is not a valid UUID";

    /** Log template emitted when the reservation key fails validation. */
    public static final String LOG_RESERVATION_INVALID_KEY =
            "Reservation rejected for upload {} and chunk {} because the key is invalid";

    /** Log template emitted when an identical reservation already exists. */
    public static final String LOG_RESERVATION_ALREADY_EXISTS =
            "Reservation already exists for upload {} and chunk {}";

    /** Log template emitted when an existing reservation has a different key. */
    public static final String LOG_RESERVATION_KEY_MISMATCH =
            "Reservation rejected because the key does not match for upload {} and chunk {}";

    /** Log template emitted after creating a reservation. */
    public static final String LOG_RESERVATION_CREATED =
            "Created reservation for upload {} and chunk {}";

    /** Log template emitted when a concurrent reservation conflicts during persistence. */
    public static final String LOG_RESERVATION_CONCURRENT_CONFLICT =
            "Concurrent reservation conflict while saving upload {} and chunk {}";

    /** Log template emitted when a concurrent request matches the persisted reservation. */
    public static final String LOG_RESERVATION_CONCURRENT_RETRY =
            "Concurrent reservation resolved as an identical retry for upload {} and chunk {}";

    /**
     * Prevents construction of this constants-only utility class.
     */
    private Constants()
    {
    }
}
