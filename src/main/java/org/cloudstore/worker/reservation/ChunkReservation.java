package org.cloudstore.worker.reservation;

import jakarta.persistence.*;
import org.cloudstore.worker.ChunkState;

import java.time.Instant;

/** Persisted assignment of one chunk to this worker. */
@Entity
@IdClass(ChunkReservationId.class)
public class ChunkReservation
{
    @Id
    private String uploadId;

    @Id
    private String chunkId;

    private String reservationKey;

    @Column(nullable = false)
    private Instant uploadTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChunkState state;

    /** Required by JPA when loading an entity from the database. */
    protected ChunkReservation()
    {
    }

    /**
     * Creates a reservation record with its initial state and timestamp.
     *
     * @param uploadId upload identifier
     * @param chunkId chunk identifier
     * @param reservationKey key the master assigned to this reservation
     * @param uploadTime time the worker accepted the reservation
     * @param state current chunk lifecycle state
     */
    public ChunkReservation(
            String uploadId,
            String chunkId,
            String reservationKey,
            Instant uploadTime,
            ChunkState state)
    {
        this.uploadId = uploadId;
        this.chunkId = chunkId;
        this.reservationKey = reservationKey;
        this.uploadTime = uploadTime;
        this.state = state;
    }

    /** Returns the key assigned to this reservation. */
    public String getReservationKey()
    {
        return reservationKey;
    }

    /** Returns the upload identifier for this reservation. */
    public String getUploadId()
    {
        return uploadId;
    }

    /** Returns the chunk identifier for this reservation. */
    public String getChunkId()
    {
        return chunkId;
    }

    /** Returns the time when the worker accepted this reservation. */
    public Instant getUploadTime()
    {
        return uploadTime;
    }

    /** Returns the current lifecycle state of the chunk. */
    public ChunkState getState()
    {
        return state;
    }
}
