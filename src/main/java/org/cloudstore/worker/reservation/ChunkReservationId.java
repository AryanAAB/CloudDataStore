package org.cloudstore.worker.reservation;

import java.io.Serializable;
import java.util.Objects;

/** Composite database identity formed by an upload ID and a chunk ID. */
public class ChunkReservationId implements Serializable
{
    private String uploadId;
    private String chunkId;

    /** Required by JPA when it creates the composite identifier. */
    public ChunkReservationId()
    {
    }

    /**
     * Creates an identity for the specified upload and chunk.
     *
     * @param uploadId upload UUID
     * @param chunkId chunk UUID
     */
    public ChunkReservationId(String uploadId, String chunkId)
    {
        this.uploadId = uploadId;
        this.chunkId = chunkId;
    }

    /** Returns the upload UUID in this identity. */
    public String getUploadId()
    {
        return uploadId;
    }

    /** Returns the chunk UUID in this identity. */
    public String getChunkId()
    {
        return chunkId;
    }

    /**
     * Compares both UUID values that form this composite identity.
     *
     * @param other object to compare
     * @return {@code true} when both IDs match
     */
    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }
        if (!(other instanceof ChunkReservationId that))
        {
            return false;
        }
        return Objects.equals(uploadId, that.uploadId)
                && Objects.equals(chunkId, that.chunkId);
    }

    /**
     * Computes a hash from both IDs that form this composite identity.
     *
     * @return hash code for this identity
     */
    @Override
    public int hashCode()
    {
        return Objects.hash(uploadId, chunkId);
    }
}
