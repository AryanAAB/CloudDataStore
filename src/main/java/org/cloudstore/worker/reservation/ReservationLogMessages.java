package org.cloudstore.worker.reservation;

/** Centralized log templates grouped by reservation operation. */
public final class ReservationLogMessages
{
    /** Log templates for the worker facade's reservation entry point. */
    public static final class Facade
    {
        /** Emitted when the facade receives a reservation request. */
        public static final String REQUEST_RECEIVED = "Received reserve operation";

        /** Emitted when the facade completes a reservation request. */
        public static final String REQUEST_COMPLETED = "Reserve operation completed: success={}";

        private Facade()
        {
        }
    }

    /** Log templates for reservation processing and persistence. */
    public static final class Processing
    {
        /** Emitted when upload or chunk identifiers fail validation. */
        public static final String INVALID_IDENTIFIERS =
                "Reservation rejected because an upload ID or chunk ID is invalid";

        /** Emitted when the reservation key fails validation. */
        public static final String INVALID_KEY =
                "Reservation rejected for upload {} and chunk {} because the key is invalid";

        /** Emitted when an identical reservation already exists. */
        public static final String ALREADY_EXISTS =
                "Reservation already exists for upload {} and chunk {}";

        /** Emitted when an existing reservation has a different key. */
        public static final String KEY_MISMATCH =
                "Reservation rejected because the key does not match for upload {} and chunk {}";

        /** Emitted after creating a reservation. */
        public static final String CREATED = "Created reservation for upload {} and chunk {}";

        /** Emitted when a concurrent reservation conflicts during persistence. */
        public static final String CONCURRENT_CONFLICT =
                "Concurrent reservation conflict while saving upload {} and chunk {}";

        /** Emitted when a concurrent request matches the persisted reservation. */
        public static final String CONCURRENT_RETRY =
                "Concurrent reservation resolved as an identical retry for upload {} and chunk {}";

        private Processing()
        {
        }
    }

    /** Log templates for scheduled reservation cleanup. */
    public static final class Cleaner
    {
        /** Emitted when a reservation cleanup pass starts. */
        public static final String CLEANUP_STARTED = "Starting expired reservation cleanup";

        /** Emitted when a reservation is expired. */
        public static final String RESERVATION_EXPIRED =
                "Expired reservation for upload {} and chunk {}";

        /** Emitted when a reservation cleanup pass completes. */
        public static final String CLEANUP_COMPLETED =
                "Expired reservation cleanup completed: expiredCount={}";

        private Cleaner()
        {
        }
    }

    /** Prevents construction of this log-template utility class. */
    private ReservationLogMessages()
    {
    }
}
