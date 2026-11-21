package org.cloudstore.controller.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Tombstone record for chunks that need deletion on workers that were DOWN during object deletion.
 */
@Entity
@Table(name = "pending_deletions")
public class PendingDeletion
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "worker_id", nullable = false, length = 128)
    private String workerId;

    @Column(name = "object_id", nullable = false, length = 128)
    private String objectId;

    @Column(name = "chunk_id", nullable = false, length = 128)
    private String chunkId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public PendingDeletion()
    {
    }

    public PendingDeletion(String workerId, String objectId, String chunkId)
    {
        this.workerId = workerId;
        this.objectId = objectId;
        this.chunkId = chunkId;
        this.createdAt = Instant.now();
    }

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public String getWorkerId()
    {
        return workerId;
    }

    public void setWorkerId(String workerId)
    {
        this.workerId = workerId;
    }

    public String getObjectId()
    {
        return objectId;
    }

    public void setObjectId(String objectId)
    {
        this.objectId = objectId;
    }

    public String getChunkId()
    {
        return chunkId;
    }

    public void setChunkId(String chunkId)
    {
        this.chunkId = chunkId;
    }

    public Instant getCreatedAt()
    {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt)
    {
        this.createdAt = createdAt;
    }
}

