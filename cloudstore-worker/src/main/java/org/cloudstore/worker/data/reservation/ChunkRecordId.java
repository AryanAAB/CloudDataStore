package org.cloudstore.worker.data.reservation;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite database identity formed by a file ID and a chunk ID.
 */
public class ChunkRecordId implements Serializable
{
    private String fileId;
    private String chunkId;

    /**
     * Required by JPA when it creates the composite identity.
     */
    public ChunkRecordId()
    {
    }

    /**
     * Creates a composite identity.
     *
     * @param fileId  file identifier
     * @param chunkId chunk identifier
     */
    public ChunkRecordId(String fileId, String chunkId)
    {
        this.fileId = fileId;
        this.chunkId = chunkId;
    }

    /**
     * Returns the file identifier.
     */
    public String getFileId()
    {
        return fileId;
    }

    /**
     * Returns the chunk identifier.
     */
    public String getChunkId()
    {
        return chunkId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }
        if (!(other instanceof ChunkRecordId that))
        {
            return false;
        }
        return Objects.equals(fileId, that.fileId)
                && Objects.equals(chunkId, that.chunkId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode()
    {
        return Objects.hash(fileId, chunkId);
    }
}
