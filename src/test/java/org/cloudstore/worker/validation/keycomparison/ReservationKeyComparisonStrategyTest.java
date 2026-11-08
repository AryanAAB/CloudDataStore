package org.cloudstore.worker.validation.keycomparison;

import org.cloudstore.worker.validation.keycomparison.strategies.ExactReservationKeyComparisonStrategy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationKeyComparisonStrategyTest
{
    @Test
    void exactStrategyRequiresMatchingCase()
    {
        ExactReservationKeyComparisonStrategy strategy = new ExactReservationKeyComparisonStrategy();

        assertThat(strategy.matches("Key-A", "Key-A")).isTrue();
        assertThat(strategy.matches("Key-A", "key-a")).isFalse();
        assertThat(strategy.matches(null, "Key-A")).isFalse();
    }
}
