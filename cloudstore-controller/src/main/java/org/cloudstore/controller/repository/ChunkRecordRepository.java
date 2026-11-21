package org.cloudstore.controller.repository;

import org.cloudstore.controller.model.ChunkRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChunkRecordRepository extends JpaRepository<ChunkRecord, String>
{
    List<ChunkRecord> findByObjectIdOrderByChunkIndexAsc(String objectId);

    void deleteByObjectId(String objectId);
}

