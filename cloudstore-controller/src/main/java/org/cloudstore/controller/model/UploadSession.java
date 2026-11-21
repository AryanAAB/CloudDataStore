package org.cloudstore.controller.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Tracks a client upload session for the 2-step resumable upload protocol.
 */
@Entity
@Table(name = "upload_sessions")
public class UploadSession
{
    public static final String STATUS_INITIATED = "INITIATED";
    public static final String STATUS_STREAMING = "STREAMING";
    public static final String STATUS_COMMITTED = "COMMITTED";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_ABORTED = "ABORTED";

    @Id
    @Column(name = "session_id", nullable = false, length = 128)
    private String sessionId;

    @Column(name = "session_key", nullable = false, length = 128)
    private String sessionKey;

    @Column(name = "object_id", nullable = false, length = 128)
    private String objectId;

    @Column(name = "object_name", nullable = false)
    private String objectName;

    @Column(name = "declared_size", nullable = false)
    private long declaredSize;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "total_chunks", nullable = false)
    private int totalChunks;

    @Column(name = "next_expected_chunk_index", nullable = false)
    private int nextExpectedChunkIndex;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "upload_session_custom_metadata", joinColumns = @JoinColumn(name = "session_id"))
    @MapKeyColumn(name = "meta_key")
    @Column(name = "meta_value")
    private Map<String, String> customMetadata = new HashMap<>();

    public UploadSession()
    {
    }

    public UploadSession(
            String sessionId,
            String sessionKey,
            String objectId,
            String objectName,
            long declaredSize,
            String contentType,
            int totalChunks,
            Instant expiresAt,
            Map<String, String> customMetadata)
    {
        this.sessionId = sessionId;
        this.sessionKey = sessionKey;
        this.objectId = objectId;
        this.objectName = objectName;
        this.declaredSize = declaredSize;
        this.contentType = contentType;
        this.totalChunks = totalChunks;
        this.nextExpectedChunkIndex = 0;
        this.status = STATUS_INITIATED;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
        this.customMetadata = customMetadata != null ? customMetadata : new HashMap<>();
    }

    public String getSessionId()
    {
        return sessionId;
    }

    public void setSessionId(String sessionId)
    {
        this.sessionId = sessionId;
    }

    public String getSessionKey()
    {
        return sessionKey;
    }

    public void setSessionKey(String sessionKey)
    {
        this.sessionKey = sessionKey;
    }

    public String getObjectId()
    {
        return objectId;
    }

    public void setObjectId(String objectId)
    {
        this.objectId = objectId;
    }

    public String getObjectName()
    {
        return objectName;
    }

    public void setObjectName(String objectName)
    {
        this.objectName = objectName;
    }

    public long getDeclaredSize()
    {
        return declaredSize;
    }

    public void setDeclaredSize(long declaredSize)
    {
        this.declaredSize = declaredSize;
    }

    public String getContentType()
    {
        return contentType;
    }

    public void setContentType(String contentType)
    {
        this.contentType = contentType;
    }

    public int getTotalChunks()
    {
        return totalChunks;
    }

    public void setTotalChunks(int totalChunks)
    {
        this.totalChunks = totalChunks;
    }

    public int getNextExpectedChunkIndex()
    {
        return nextExpectedChunkIndex;
    }

    public void setNextExpectedChunkIndex(int nextExpectedChunkIndex)
    {
        this.nextExpectedChunkIndex = nextExpectedChunkIndex;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Instant getCreatedAt()
    {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt)
    {
        this.createdAt = createdAt;
    }

    public Instant getExpiresAt()
    {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt)
    {
        this.expiresAt = expiresAt;
    }

    public Map<String, String> getCustomMetadata()
    {
        return customMetadata;
    }

    public void setCustomMetadata(Map<String, String> customMetadata)
    {
        this.customMetadata = customMetadata != null ? customMetadata : new HashMap<>();
    }

    public boolean isExpired()
    {
        return Instant.now().isAfter(expiresAt);
    }
}

