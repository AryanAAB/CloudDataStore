package org.cloudstore.worker.validation.key;

/**
 * Defines validation rules for an optional worker reservation key.
 */
public interface ReservationKeyValidator
{
    /**
     * Returns whether a supplied reservation key violates the worker's rules.
     *
     * @param key optional reservation key, which may be {@code null}
     * @return {@code true} when the key is invalid
     */
    boolean isInvalid(String key);
}
