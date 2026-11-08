package org.cloudstore.worker.validation.constants;

/**
 * Length limits used by worker identifier validators.
 */
public final class ValidationConstants
{
    /**
     * Maximum number of characters accepted for upload and chunk identifiers.
     */
    public static final int MAX_IDENTIFIER_LENGTH = 255;

    /**
     * Maximum number of characters accepted for a non-null reservation key.
     */
    public static final int MAX_RESERVATION_KEY_LENGTH = 128;

    /**
     * Prevents construction of this constants-only class.
     */
    private ValidationConstants()
    {
    }
}
