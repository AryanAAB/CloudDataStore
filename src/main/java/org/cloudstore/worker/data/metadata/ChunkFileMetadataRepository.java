package org.cloudstore.worker.data.metadata;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

/**
 * Database operations for verified chunk-file metadata.
 */
public interface ChunkFileMetadataRepository
        extends JpaRepository<ChunkFileMetadata, ChunkFileMetadataId>
{
    /**
     * Finds metadata while locking the row for a metadata replacement transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ChunkFileMetadata> findByFileIdAndChunkId(String fileId, String chunkId);

    /**
     * Checks whether any metadata row still references the supplied storage path.
     */
    boolean existsByFileLocation(String fileLocation);
}
