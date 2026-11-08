package org.cloudstore.worker.validation.key;

/**
 * Defines validation rules for a required worker reservation key.
 */
public interface ReservationKeyValidator
{
    /**
     * Returns whether a supplied reservation key violates the worker's rules.
     *
     * @param key reservation key
     * @return {@code true} when the key is invalid
     */
    boolean isInvalid(String key);
}
