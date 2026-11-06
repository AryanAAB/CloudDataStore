package org.cloudstore.worker.reservation.cleaner;

import org.cloudstore.worker.ChunkState;
import org.cloudstore.worker.Constants;
import org.cloudstore.worker.reservation.ChunkReservation;
import org.cloudstore.worker.reservation.ChunkReservationId;
import org.cloudstore.worker.reservation.ChunkReservationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Integration tests for reservation expiration and persistence. */
@DataJpaTest
class WorkerReservationCleanerTest
{
    private static final String UPLOAD_ID = "upload-cleaner-test";
    private static final String EXPIRED_CHUNK_ID = "expired-chunk";
    private static final String ACTIVE_CHUNK_ID = "active-chunk";
    private static final String COMMITTED_CHUNK_ID = "committed-chunk";

    @Autowired
    private ChunkReservationRepository reservations;

    @Test
    void marksReservationExpiredWhenItExceedsTimeout()
    {
        ChunkReservation expiredReservation = reservation(
                EXPIRED_CHUNK_ID,
                Instant.now().minusMillis(Constants.RESERVATION_TIMEOUT_MILLIS + 1_000L),
                ChunkState.RESERVED);
        reservations.saveAndFlush(expiredReservation);
        WorkerReservationCleaner cleaner = new WorkerReservationCleaner(reservations);

        cleaner.cleanExpiredReservations();

        ChunkReservation storedReservation = reservations.findById(
                new ChunkReservationId(UPLOAD_ID, EXPIRED_CHUNK_ID)).orElseThrow();
        assertThat(storedReservation.getState()).isEqualTo(ChunkState.EXPIRED);
    }

    @Test
    void leavesReservationReservedWhenItHasNotReachedTimeout()
    {
        ChunkReservation activeReservation = reservation(
                ACTIVE_CHUNK_ID,
                Instant.now().minusMillis(Constants.RESERVATION_TIMEOUT_MILLIS - 1_000L),
                ChunkState.RESERVED);
        reservations.saveAndFlush(activeReservation);
        WorkerReservationCleaner cleaner = new WorkerReservationCleaner(reservations);

        cleaner.cleanExpiredReservations();

        ChunkReservation storedReservation = reservations.findById(
                new ChunkReservationId(UPLOAD_ID, ACTIVE_CHUNK_ID)).orElseThrow();
        assertThat(storedReservation.getState()).isEqualTo(ChunkState.RESERVED);
    }

    @Test
    void ignoresOldReservationsThatAreNotInReservedState()
    {
        ChunkReservation committedReservation = reservation(
                COMMITTED_CHUNK_ID,
                Instant.now().minusMillis(Constants.RESERVATION_TIMEOUT_MILLIS + 1_000L),
                ChunkState.COMMITTED);
        reservations.saveAndFlush(committedReservation);
        WorkerReservationCleaner cleaner = new WorkerReservationCleaner(reservations);

        cleaner.cleanExpiredReservations();

        ChunkReservation storedReservation = reservations.findById(
                new ChunkReservationId(UPLOAD_ID, COMMITTED_CHUNK_ID)).orElseThrow();
        assertThat(storedReservation.getState()).isEqualTo(ChunkState.COMMITTED);
        assertThat(reservations.findAllByState(ChunkState.EXPIRED)).isEmpty();
    }

    @Test
    void doesNothingWhenThereAreNoReservations()
    {
        WorkerReservationCleaner cleaner = new WorkerReservationCleaner(reservations);

        cleaner.cleanExpiredReservations();

        assertThat(reservations.findAllByState(ChunkState.EXPIRED)).isEmpty();
    }

    private static ChunkReservation reservation(String chunkId, Instant uploadTime, ChunkState state)
    {
        return new ChunkReservation(UPLOAD_ID, chunkId, null, uploadTime, state);
    }
}
