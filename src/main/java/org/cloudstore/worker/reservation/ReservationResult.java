package org.cloudstore.worker.reservation;

/**
 * Outcome returned by a worker reservation operation.
 *
 * @param success whether the reservation request succeeded
 * @param message human-readable result details
 */
public record ReservationResult(boolean success, String message)
{
    /**
     * Creates a successful result with the supplied message.
     *
     * @param message human-readable success details
     * @return a successful result
     */
    public static ReservationResult success(String message)
    {
        return new ReservationResult(true, message);
    }

    /**
     * Creates a failed result with the supplied message.
     *
     * @param message human-readable failure details
     */
    public static ReservationResult failure(String message)
    {
        return new ReservationResult(false, message);
    }
}
