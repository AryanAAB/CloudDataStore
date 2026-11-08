package org.cloudstore.worker.validation.key;

import org.cloudstore.worker.validation.constants.ValidationConstants;
import org.springframework.stereotype.Component;

/**
 * Accepts a null key or a non-blank key within the configured length limit.
 */
@Component
public class OptionalReservationKeyValidator implements ReservationKeyValidator
{
    /**
     * Checks whether a non-null key is blank or too long.
     *
     * @param key optional reservation key
     * @return {@code true} when the key is invalid
     */
    @Override
    public boolean isInvalid(String key)
    {
        return key != null
                && (key.isBlank()
                || key.length() > ValidationConstants.MAX_RESERVATION_KEY_LENGTH);
    }
}
