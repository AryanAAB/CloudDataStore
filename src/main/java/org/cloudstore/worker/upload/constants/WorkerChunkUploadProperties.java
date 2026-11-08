package org.cloudstore.worker.upload.constants;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Tunable settings for chunk upload streaming.
 */
@Component
@ConfigurationProperties(prefix = "worker.upload")
public class WorkerChunkUploadProperties
{
    private int bufferSizeBytes;

    /**
     * Returns the buffer size used when streaming chunk content to disk.
     */
    public int getBufferSizeBytes()
    {
        return bufferSizeBytes;
    }

    /**
     * Sets the buffer size used when streaming chunk content to disk.
     */
    public void setBufferSizeBytes(int bufferSizeBytes)
    {
        this.bufferSizeBytes = bufferSizeBytes;
    }
}
