package org.cloudstore.worker.key;

import org.cloudstore.worker.Constants;
import org.springframework.stereotype.Component;

/**
 * Accepts non-empty opaque string identifiers without assuming a specific format.
 */
@Component
public class OpaqueStringReservationIdentifierValidator implements ReservationIdentifierValidator
{
    /**
     * Checks that the identifier is present, non-blank, and within the configured length limit.
     * The identifier is otherwise treated as opaque and is not parsed or rewritten.
     *
     * @param identifier identifier to validate
     * @return {@code true} when the identifier is accepted
     */
    @Override
    public boolean isValid(String identifier)
    {
        return identifier == null
                || identifier.isBlank()
                || identifier.length() > Constants.MAX_RESERVATION_ID_LENGTH;
    }
}
