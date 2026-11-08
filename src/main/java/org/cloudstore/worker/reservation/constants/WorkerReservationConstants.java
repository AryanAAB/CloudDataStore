package org.cloudstore.worker.reservation.constants;

/**
 * Limits used by worker reservation processing.
 */
public final class WorkerReservationConstants
{
    /**
     * Length of a reservation lease in milliseconds.
     */
    public static final long RESERVATION_TTL_MILLIS = 60_000L;

    /**
     * Prevents construction of this constants-only class.
     */
    private WorkerReservationConstants()
    {
    }
}
