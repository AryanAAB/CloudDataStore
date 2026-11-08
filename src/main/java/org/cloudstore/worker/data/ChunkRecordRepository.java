package org.cloudstore.worker.data;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Database operations for persisted worker chunk records.
 */
public interface ChunkRecordRepository
        extends JpaRepository<ChunkRecord, ChunkRecordId>
{
}
