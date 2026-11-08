package org.cloudstore.worker.data;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.Instant;
import java.util.Optional;

/**
 * Database operations for persisted worker chunk records.
 */
public interface ChunkRecordRepository
        extends JpaRepository<ChunkRecord, ChunkRecordId>
{
    /**
     * Finds a chunk reservation while holding a write lock for a short transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ChunkRecord> findByFileIdAndChunkId(String fileId, String chunkId);

    /**
     * Deletes records whose expiration time is at or before the supplied instant.
     *
     * @param expirationTime expiration cutoff
     * @return number of records deleted
     */
    long deleteByExpiresAtLessThanEqual(Instant expirationTime);
}
