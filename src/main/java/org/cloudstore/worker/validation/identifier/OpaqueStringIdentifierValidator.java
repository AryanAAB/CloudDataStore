package org.cloudstore.worker.validation.identifier;

import org.cloudstore.worker.validation.constants.ValidationConstants;
import org.springframework.stereotype.Component;

/**
 * Accepts non-blank string identifiers without requiring a particular format.
 */
@Component
public class OpaqueStringIdentifierValidator implements IdentifierValidator
{
    /**
     * Checks whether an identifier is blank or exceeds the configured length limit.
     *
     * @param identifier identifier to validate
     * @return {@code true} when invalid
     */
    @Override
    public boolean isInvalid(String identifier)
    {
        return identifier == null
                || identifier.isBlank()
                || identifier.length() > ValidationConstants.MAX_IDENTIFIER_LENGTH;
    }
}
