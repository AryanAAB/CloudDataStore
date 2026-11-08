package org.cloudstore.worker.reservation.renewal;

/**
 * Supported reservation key renewal policies.
 */
public enum ReservationRenewalModes
{
    /**
     * Allows replacing an active reservation key.
     */
    ALWAYS_ACCEPT,

    /**
     * Allows replacing a reservation key only after the current reservation expires.
     */
    AFTER_EXPIRATION
}
