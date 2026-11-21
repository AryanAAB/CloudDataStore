package org.cloudstore.controller.repository;

import org.cloudstore.controller.model.PendingDeletion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PendingDeletionRepository extends JpaRepository<PendingDeletion, Long>
{
    List<PendingDeletion> findByWorkerId(String workerId);

    void deleteByWorkerIdAndChunkId(String workerId, String chunkId);
}

