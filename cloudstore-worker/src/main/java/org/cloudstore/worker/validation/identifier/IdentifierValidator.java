package org.cloudstore.worker.validation.identifier;

/**
 * Defines validation rules for upload and chunk identifiers.
 */
public interface IdentifierValidator
{
    /**
     * Returns whether an identifier violates the worker's identifier rules.
     *
     * @param identifier identifier to validate
     * @return {@code true} when the identifier is invalid
     */
    boolean isInvalid(String identifier);
}
