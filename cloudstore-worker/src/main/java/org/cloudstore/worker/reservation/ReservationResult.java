package org.cloudstore.worker.reservation;

/**
 * Outcome of a worker reservation request.
 */
public record ReservationResult(boolean success, String message, FailureCategory failureCategory)
{
    /**
     * Broad category for mapping failed reservations to a protocol response.
     */
    public enum FailureCategory
    {
        INVALID_REQUEST,
        CONFLICT
    }

    /**
     * Creates a successful reservation result.
     *
     * @param message result message
     * @return successful result
     */
    public static ReservationResult success(String message)
    {
        return new ReservationResult(true, message, null);
    }

    /**
     * Creates a failed reservation result.
     *
     * @param message result message
     * @return failed result
     */
    public static ReservationResult failure(String message, FailureCategory failureCategory)
    {
        return new ReservationResult(false, message, failureCategory);
    }
}
