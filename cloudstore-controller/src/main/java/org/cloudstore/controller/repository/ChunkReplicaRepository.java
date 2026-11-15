package org.cloudstore.controller.repository;

import org.cloudstore.controller.model.ChunkReplica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChunkReplicaRepository extends JpaRepository<ChunkReplica, Long>
{
    List<ChunkReplica> findByChunkId(String chunkId);

    Optional<ChunkReplica> findByChunkIdAndWorkerId(String chunkId, String workerId);

    void deleteByChunkId(String chunkId);

    @Query("SELECT r FROM ChunkReplica r WHERE r.chunkId = :chunkId AND r.replicaState = 'OK'")
    List<ChunkReplica> findHealthyByChunkId(String chunkId);

    @Query("SELECT r.chunkId, COUNT(r) FROM ChunkReplica r " +
            "JOIN WorkerNode w ON r.workerId = w.workerId " +
            "WHERE r.replicaState = 'OK' AND w.status = 'UP' " +
            "GROUP BY r.chunkId HAVING COUNT(r) < :requiredReplicas")
    List<Object[]> findUnderReplicatedChunks(long requiredReplicas);
}

