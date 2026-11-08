package org.cloudstore.worker.upload.constants;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * Configuration for where this worker stores uploaded chunk files.
 */
@Component
@ConfigurationProperties(prefix = "worker.storage")
public class WorkerChunkStorageProperties
{
    private Path rootDirectory;

    /**
     * Returns the root directory used for chunk files.
     */
    public Path getRootDirectory()
    {
        return rootDirectory;
    }

    /**
     * Sets the root directory used for chunk files.
     */
    public void setRootDirectory(Path rootDirectory)
    {
        this.rootDirectory = rootDirectory;
    }
}
