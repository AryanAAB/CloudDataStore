package org.cloudstore.controller.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Metadata record for a user object stored across the cluster.
 */
@Entity
@Table(name = "stored_objects")
public class StoredObject
{
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_COMMITTED = "COMMITTED";
    public static final String STATUS_DELETED = "DELETED";

    @Id
    @Column(name = "object_id", nullable = false, length = 128)
    private String objectId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "size", nullable = false)
    private long size;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "checksum", length = 64)
    private String checksum;

    @Column(name = "total_chunks", nullable = false)
    private int totalChunks;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "stored_object_custom_metadata", joinColumns = @JoinColumn(name = "object_id"))
    @MapKeyColumn(name = "meta_key")
    @Column(name = "meta_value")
    private Map<String, String> customMetadata = new HashMap<>();

    public StoredObject()
    {
    }

    public StoredObject(String objectId, String name, long size, String contentType, int totalChunks)
    {
        this.objectId = objectId;
        this.name = name;
        this.size = size;
        this.contentType = contentType;
        this.totalChunks = totalChunks;
        this.status = STATUS_PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public String getObjectId()
    {
        return objectId;
    }

    public void setObjectId(String objectId)
    {
        this.objectId = objectId;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public long getSize()
    {
        return size;
    }

    public void setSize(long size)
    {
        this.size = size;
    }

    public String getContentType()
    {
        return contentType;
    }

    public void setContentType(String contentType)
    {
        this.contentType = contentType;
    }

    public String getChecksum()
    {
        return checksum;
    }

    public void setChecksum(String checksum)
    {
        this.checksum = checksum;
    }

    public int getTotalChunks()
    {
        return totalChunks;
    }

    public void setTotalChunks(int totalChunks)
    {
        this.totalChunks = totalChunks;
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

    public Instant getUpdatedAt()
    {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt)
    {
        this.updatedAt = updatedAt;
    }

    public Map<String, String> getCustomMetadata()
    {
        return customMetadata;
    }

    public void setCustomMetadata(Map<String, String> customMetadata)
    {
        this.customMetadata = customMetadata != null ? customMetadata : new HashMap<>();
    }
}

