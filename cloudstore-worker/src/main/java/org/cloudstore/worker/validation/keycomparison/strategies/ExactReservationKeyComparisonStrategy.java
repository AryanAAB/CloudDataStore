package org.cloudstore.worker.validation.keycomparison.strategies;

import java.util.Objects;

/**
 * Matches reservation keys only when both strings are exactly equal.
 */
public class ExactReservationKeyComparisonStrategy implements ReservationKeyComparisonStrategy
{
    /**
     * Compares both keys without changing their contents.
     */
    @Override
    public boolean matches(String storedKey, String requestedKey)
    {
        return Objects.equals(storedKey, requestedKey);
    }
}
