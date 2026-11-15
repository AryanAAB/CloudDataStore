package org.cloudstore.controller.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Tracks an individual 1 MB chunk of a stored object.
 */
@Entity
@Table(name = "chunk_records")
public class ChunkRecord
{
    @Id
    @Column(name = "chunk_id", nullable = false, length = 128)
    private String chunkId;

    @Column(name = "object_id", nullable = false, length = 128)
    private String objectId;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "chunk_size", nullable = false)
    private long chunkSize;

    @Column(name = "sha256_hash", nullable = false, length = 64)
    private String sha256Hash;

    public ChunkRecord()
    {
    }

    public ChunkRecord(String chunkId, String objectId, int chunkIndex, long chunkSize, String sha256Hash)
    {
        this.chunkId = chunkId;
        this.objectId = objectId;
        this.chunkIndex = chunkIndex;
        this.chunkSize = chunkSize;
        this.sha256Hash = sha256Hash;
    }

    public String getChunkId()
    {
        return chunkId;
    }

    public void setChunkId(String chunkId)
    {
        this.chunkId = chunkId;
    }

    public String getObjectId()
    {
        return objectId;
    }

    public void setObjectId(String objectId)
    {
        this.objectId = objectId;
    }

    public int getChunkIndex()
    {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex)
    {
        this.chunkIndex = chunkIndex;
    }

    public long getChunkSize()
    {
        return chunkSize;
    }

    public void setChunkSize(long chunkSize)
    {
        this.chunkSize = chunkSize;
    }

    public String getSha256Hash()
    {
        return sha256Hash;
    }

    public void setSha256Hash(String sha256Hash)
    {
        this.sha256Hash = sha256Hash;
    }
}

