package org.cloudstore.worker.reservation;

/**
 * Outcome of a worker reservation request.
 */
public record ReservationResult(boolean success, String message)
{
    /**
     * Creates a successful reservation result.
     *
     * @param message result message
     * @return successful result
     */
    public static ReservationResult success(String message)
    {
        return new ReservationResult(true, message);
    }

    /**
     * Creates a failed reservation result.
     *
     * @param message result message
     * @return failed result
     */
    public static ReservationResult failure(String message)
    {
        return new ReservationResult(false, message);
    }
}
