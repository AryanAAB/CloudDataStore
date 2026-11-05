package org.cloudstore.worker.reservation;

import org.springframework.data.jpa.repository.JpaRepository;

/** Database operations for persisted chunk reservations. */
public interface ChunkReservationRepository
        extends JpaRepository<ChunkReservation, ChunkReservationId>
{
}
