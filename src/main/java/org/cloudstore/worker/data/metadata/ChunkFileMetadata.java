package org.cloudstore.worker.data.metadata;

import jakarta.persistence.*;
import org.cloudstore.worker.data.constants.ChunkFileMetadataConstants;

/**
 * Database metadata describing a verified chunk stored on this worker.
 */
@Entity
@Table(name = ChunkFileMetadataConstants.TABLE_NAME)
@IdClass(ChunkFileMetadataId.class)
public class ChunkFileMetadata
{
    @Id
    @Column(name = ChunkFileMetadataConstants.FILE_ID_COLUMN, nullable = false)
    private String fileId;

    @Id
    @Column(name = ChunkFileMetadataConstants.CHUNK_ID_COLUMN, nullable = false)
    private String chunkId;

    @Column(
            name = ChunkFileMetadataConstants.FILE_LOCATION_COLUMN,
            nullable = false,
            length = ChunkFileMetadataConstants.FILE_LOCATION_COLUMN_LENGTH)
    private String fileLocation;

    @Column(name = ChunkFileMetadataConstants.FILE_SIZE_COLUMN, nullable = false)
    private long fileSize;

    @Column(
            name = ChunkFileMetadataConstants.CHECKSUM_COLUMN,
            nullable = false,
            length = ChunkFileMetadataConstants.CHECKSUM_COLUMN_LENGTH)
    private String checksum;

    /**
     * Required by JPA when loading metadata.
     */
    protected ChunkFileMetadata()
    {
    }

    /**
     * Creates metadata for a verified chunk file.
     */
    public ChunkFileMetadata(
            String fileId,
            String chunkId,
            String fileLocation,
            long fileSize,
            String checksum)
    {
        this.fileId = fileId;
        this.chunkId = chunkId;
        this.fileLocation = fileLocation;
        this.fileSize = fileSize;
        this.checksum = checksum;
    }

    /**
     * Returns the owning file identifier.
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
     * Returns the worker-local path where the chunk file is stored.
     */
    public String getFileLocation()
    {
        return fileLocation;
    }

    /**
     * Returns the verified chunk size in bytes.
     */
    public long getFileSize()
    {
        return fileSize;
    }

    /**
     * Returns the verified SHA-256 checksum in hexadecimal form.
     */
    public String getChecksum()
    {
        return checksum;
    }
}
