package org.cloudstore.worker.validation.keycomparison.strategies;

/**
 * Defines when two reservation keys should be treated as the same key.
 */
public interface ReservationKeyComparisonStrategy
{
    /**
     * Returns whether a stored key matches a key supplied by the caller.
     */
    boolean matches(String storedKey, String requestedKey);
}
