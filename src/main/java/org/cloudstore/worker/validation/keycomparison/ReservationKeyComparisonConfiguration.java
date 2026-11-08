package org.cloudstore.worker.validation.keycomparison;

import org.cloudstore.worker.validation.keycomparison.strategies.ExactReservationKeyComparisonStrategy;
import org.cloudstore.worker.validation.keycomparison.strategies.ReservationKeyComparisonStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the worker's default exact key comparison strategy.
 */
@Configuration(proxyBeanMethods = false)
public class ReservationKeyComparisonConfiguration
{
    /**
     * Creates the exact key comparison strategy.
     */
    @Bean
    public ReservationKeyComparisonStrategy reservationKeyComparisonStrategy()
    {
        return new ExactReservationKeyComparisonStrategy();
    }
}
