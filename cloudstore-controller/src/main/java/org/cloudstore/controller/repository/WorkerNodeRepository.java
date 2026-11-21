package org.cloudstore.controller.repository;

import org.cloudstore.controller.model.WorkerNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface WorkerNodeRepository extends JpaRepository<WorkerNode, String>
{
    List<WorkerNode> findByStatus(String status);

    @Modifying
    @Query("UPDATE WorkerNode w SET w.status = 'DOWN' WHERE w.lastHeartbeat < :cutoff AND w.status = 'UP'")
    int markStaleWorkersDown(Instant cutoff);
}

