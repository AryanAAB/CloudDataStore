package org.cloudstore.worker.reservation.renewal;

import org.cloudstore.worker.reservation.constants.WorkerReservationProperties;
import org.cloudstore.worker.reservation.renewal.strategies.AfterExpirationKeyRenewalStrategy;
import org.cloudstore.worker.reservation.renewal.strategies.AlwaysAcceptKeyRenewalStrategy;
import org.cloudstore.worker.reservation.renewal.strategies.ReservationRenewalStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Selects the reservation key renewal strategy configured for this worker.
 */
@Configuration(proxyBeanMethods = false)
public class ReservationRenewalConfiguration
{
    /**
     * Creates the strategy selected by the worker reservation settings.
     */
    @Bean
    public ReservationRenewalStrategy reservationRenewalStrategy(
            WorkerReservationProperties reservationProperties)
    {
        if (reservationProperties.getKeyRenewalPolicy() == ReservationRenewalModes.ALWAYS_ACCEPT)
        {
            return new AlwaysAcceptKeyRenewalStrategy();
        }
        return new AfterExpirationKeyRenewalStrategy();
    }
}
