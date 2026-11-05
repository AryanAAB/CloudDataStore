package org.cloudstore.worker.key;

/**
 * Defines the validation policy for upload and chunk identifiers.
 */
public interface ReservationIdentifierValidator
{
    /**
     * Determines whether an identifier is accepted by the worker.
     *
     * @param identifier identifier to validate
     * @return {@code true} when the identifier is valid
     */
    boolean isValid(String identifier);
}
