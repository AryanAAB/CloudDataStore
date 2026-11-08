package org.cloudstore.worker.data;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;

import java.time.Instant;

/**
 * Persistent worker record for a chunk assigned to a file.
 */
@Entity
@IdClass(ChunkRecordId.class)
public class ChunkRecord
{
    @Id
    private String fileId;

    @Id
    private String chunkId;

    private String reservationKey;

    @Column(nullable = false)
    private Instant expiresAt;

    /**
     * Required by JPA when loading a worker chunk record.
     */
    protected ChunkRecord()
    {
    }

    /**
     * Creates a worker chunk record.
     *
     * @param fileId         file identifier
     * @param chunkId        chunk identifier
     * @param reservationKey optional reservation key
     * @param expiresAt      expiration time of this reservation
     */
    public ChunkRecord(
            String fileId,
            String chunkId,
            String reservationKey,
            Instant expiresAt)
    {
        this.fileId = fileId;
        this.chunkId = chunkId;
        this.reservationKey = reservationKey;
        this.expiresAt = expiresAt;
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
     * Returns the optional reservation key.
     */
    public String getReservationKey()
    {
        return reservationKey;
    }

    /**
     * Returns the reservation expiration time.
     */
    public Instant getExpiresAt()
    {
        return expiresAt;
    }

    /**
     * Updates the record's reservation key and expiration time.
     */
    public void renew(String reservationKey, Instant expiresAt)
    {
        this.reservationKey = reservationKey;
        this.expiresAt = expiresAt;
    }
}
