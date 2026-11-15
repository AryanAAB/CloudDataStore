package org.cloudstore.controller.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Tracks the placement and integrity state of a chunk copy on a specific worker.
 */
@Entity
@Table(name = "chunk_replicas")
public class ChunkReplica
{
    public static final String STATE_OK = "OK";
    public static final String STATE_CORRUPT = "CORRUPT";
    public static final String STATE_MISSING = "MISSING";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chunk_id", nullable = false, length = 128)
    private String chunkId;

    @Column(name = "worker_id", nullable = false, length = 128)
    private String workerId;

    @Column(name = "replica_state", nullable = false, length = 32)
    private String replicaState;

    @Column(name = "last_verified_at", nullable = false)
    private Instant lastVerifiedAt;

    public ChunkReplica()
    {
    }

    public ChunkReplica(String chunkId, String workerId)
    {
        this.chunkId = chunkId;
        this.workerId = workerId;
        this.replicaState = STATE_OK;
        this.lastVerifiedAt = Instant.now();
    }

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public String getChunkId()
    {
        return chunkId;
    }

    public void setChunkId(String chunkId)
    {
        this.chunkId = chunkId;
    }

    public String getWorkerId()
    {
        return workerId;
    }

    public void setWorkerId(String workerId)
    {
        this.workerId = workerId;
    }

    public String getReplicaState()
    {
        return replicaState;
    }

    public void setReplicaState(String replicaState)
    {
        this.replicaState = replicaState;
    }

    public Instant getLastVerifiedAt()
    {
        return lastVerifiedAt;
    }

    public void setLastVerifiedAt(Instant lastVerifiedAt)
    {
        this.lastVerifiedAt = lastVerifiedAt;
    }

    public boolean isHealthy()
    {
        return STATE_OK.equalsIgnoreCase(replicaState);
    }
}

