package org.cloudstore.worker.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.cloudstore.worker.ChunkState;

import java.util.List;

/** Database operations for persisted chunk reservations. */
public interface ChunkReservationRepository
        extends JpaRepository<ChunkReservation, ChunkReservationId>
{
    /**
     * Finds reservations in the requested lifecycle state.
     *
     * @param state lifecycle state to match
     * @return matching reservations
     */
    List<ChunkReservation> findAllByState(ChunkState state);
}
