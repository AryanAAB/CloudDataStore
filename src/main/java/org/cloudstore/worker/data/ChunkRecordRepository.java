package org.cloudstore.worker.data;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

/**
 * Database operations for persisted worker chunk records.
 */
public interface ChunkRecordRepository
        extends JpaRepository<ChunkRecord, ChunkRecordId>
{
    /**
     * Deletes records whose expiration time is at or before the supplied instant.
     *
     * @param expirationTime expiration cutoff
     * @return number of records deleted
     */
    long deleteByExpiresAtLessThanEqual(Instant expirationTime);
}
