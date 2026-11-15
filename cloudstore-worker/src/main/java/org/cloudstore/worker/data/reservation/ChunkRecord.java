package org.cloudstore.worker.data.reservation;

import jakarta.persistence.*;
import org.cloudstore.worker.data.constants.ChunkRecordConstants;
import org.cloudstore.worker.data.constants.DatabaseColumnConstants;

import java.time.Instant;

/**
 * Persistent worker record for a chunk assigned to a file.
 */
@Entity
@Table(name = ChunkRecordConstants.TABLE_NAME)
@IdClass(ChunkRecordId.class)
public class ChunkRecord
{
    @Id
    @Column(name = DatabaseColumnConstants.FILE_ID, nullable = false)
    private String fileId;

    @Id
    @Column(name = DatabaseColumnConstants.CHUNK_ID, nullable = false)
    private String chunkId;

    @Column(name = ChunkRecordConstants.RESERVATION_KEY_COLUMN)
    private String reservationKey;

    @Column(name = ChunkRecordConstants.EXPIRES_AT_COLUMN, nullable = false)
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
