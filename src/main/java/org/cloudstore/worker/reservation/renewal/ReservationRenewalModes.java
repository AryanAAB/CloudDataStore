package org.cloudstore.worker.reservation.renewal;

/**
 * Supported reservation key renewal policies.
 */
public final class ReservationRenewalModes
{
    /**
     * Allows replacing an active reservation key.
     */
    public static final String ALWAYS_ACCEPT = "always-accept";

    /**
     * Allows replacing a reservation key only after the current reservation expires.
     */
    public static final String AFTER_EXPIRATION = "after-expiration";

    /**
     * Prevents construction of this constants-only class.
     */
    private ReservationRenewalModes()
    {
    }
}
