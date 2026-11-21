package org.cloudstore.worker.validation.constants;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Runtime limits used to validate worker identifiers and reservation keys.
 */
@Component
@ConfigurationProperties(prefix = "worker.validation")
public class WorkerValidationProperties
{
    private int maxIdentifierLength;
    private int maxReservationKeyLength;

    /**
     * Returns the maximum length accepted for file and chunk identifiers.
     */
    public int getMaxIdentifierLength()
    {
        return maxIdentifierLength;
    }

    /**
     * Sets the maximum length accepted for file and chunk identifiers.
     */
    public void setMaxIdentifierLength(int maxIdentifierLength)
    {
        this.maxIdentifierLength = maxIdentifierLength;
    }

    /**
     * Returns the maximum length accepted for a reservation key.
     */
    public int getMaxReservationKeyLength()
    {
        return maxReservationKeyLength;
    }

    /**
     * Sets the maximum length accepted for a reservation key.
     */
    public void setMaxReservationKeyLength(int maxReservationKeyLength)
    {
        this.maxReservationKeyLength = maxReservationKeyLength;
    }
}
