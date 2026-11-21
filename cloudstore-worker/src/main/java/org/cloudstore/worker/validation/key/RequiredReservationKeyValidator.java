package org.cloudstore.worker.validation.key;

import org.cloudstore.worker.validation.constants.WorkerValidationProperties;
import org.springframework.stereotype.Component;

/**
 * Accepts non-blank reservation keys within the configured length limit.
 */
@Component
public class RequiredReservationKeyValidator implements ReservationKeyValidator
{
    private final WorkerValidationProperties validationProperties;

    /**
     * Creates the validator with configured reservation key limits.
     */
    public RequiredReservationKeyValidator(WorkerValidationProperties validationProperties)
    {
        this.validationProperties = validationProperties;
    }

    /**
     * Checks whether a reservation key is missing, blank, or too long.
     */
    @Override
    public boolean isInvalid(String key)
    {
        return key == null
                || key.isBlank()
                || key.length() > validationProperties.getMaxReservationKeyLength();
    }
}
