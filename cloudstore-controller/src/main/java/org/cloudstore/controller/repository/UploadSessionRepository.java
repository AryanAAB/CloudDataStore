package org.cloudstore.controller.repository;

import org.cloudstore.controller.model.UploadSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface UploadSessionRepository extends JpaRepository<UploadSession, String>
{
    Optional<UploadSession> findBySessionIdAndSessionKey(String sessionId, String sessionKey);

    List<UploadSession> findByStatusAndExpiresAtBefore(String status, Instant cutoff);
}

