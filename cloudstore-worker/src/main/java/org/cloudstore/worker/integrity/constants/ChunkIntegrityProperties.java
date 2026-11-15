package org.cloudstore.worker.integrity.constants;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Shared tuning options for streaming chunk integrity verification.
 */
@Component
@ConfigurationProperties(prefix = "worker.integrity")
public class ChunkIntegrityProperties
{
    private int bufferSizeBytes = 8192;

    public int getBufferSizeBytes()
    {
        return bufferSizeBytes;
    }

    public void setBufferSizeBytes(int bufferSizeBytes)
    {
        this.bufferSizeBytes = bufferSizeBytes;
    }
}
