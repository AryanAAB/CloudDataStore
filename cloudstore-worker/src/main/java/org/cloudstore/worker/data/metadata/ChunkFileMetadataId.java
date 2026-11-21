package org.cloudstore.worker.data.metadata;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * Composite identifier for the file metadata of one worker chunk.
 */
public class ChunkFileMetadataId implements Serializable
{
    @Serial
    private static final long serialVersionUID = 1L;

    private String fileId;
    private String chunkId;

    /**
     * Required by JPA for the composite identifier.
     */
    public ChunkFileMetadataId()
    {
    }

    /**
     * Creates a metadata ID from the owning file and chunk IDs.
     */
    public ChunkFileMetadataId(String fileId, String chunkId)
    {
        this.fileId = fileId;
        this.chunkId = chunkId;
    }

    /**
     * Compares both components of the composite identifier.
     */
    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }
        if (!(other instanceof ChunkFileMetadataId that))
        {
            return false;
        }
        return Objects.equals(fileId, that.fileId) && Objects.equals(chunkId, that.chunkId);
    }

    /**
     * Returns a hash code derived from both identifier components.
     */
    @Override
    public int hashCode()
    {
        return Objects.hash(fileId, chunkId);
    }
}
