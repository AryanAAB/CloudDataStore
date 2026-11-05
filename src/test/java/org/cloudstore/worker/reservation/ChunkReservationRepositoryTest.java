package org.cloudstore.worker.reservation;

import org.cloudstore.worker.ChunkState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for reservation persistence and composite identity.
 */
@DataJpaTest
class ChunkReservationRepositoryTest
{
    @Autowired
    private ChunkReservationRepository reservations;

    @Test
    void persistsAndLooksUpReservationByUploadAndChunkIds()
    {
        ChunkReservation reservation = reservation(
                "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                "key-one");
        reservations.saveAndFlush(reservation);

        ChunkReservation found = reservations.findById(new ChunkReservationId(
                        reservation.getUploadId(), reservation.getChunkId()))
                .orElseThrow();

        assertThat(found.getState()).isEqualTo(ChunkState.RESERVED);
        assertThat(found.getReservationKey()).isEqualTo("key-one");
    }

    @Test
    void allowsSameReservationKeyForDifferentChunks()
    {
        reservations.saveAndFlush(reservation(
                "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                "reusable-key"));
        reservations.saveAndFlush(reservation(
                "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                "cccccccc-cccc-cccc-cccc-cccccccccccc",
                "reusable-key"));

        assertThat(reservations.count()).isEqualTo(2);
    }

    @Test
    void allowsNullReservationKey()
    {
        ChunkReservation reservation = reservation(
                "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                null);

        reservations.saveAndFlush(reservation);

        assertThat(reservations.findById(new ChunkReservationId(
                "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"))).isPresent();
    }

    private static ChunkReservation reservation(String uploadId, String chunkId, String key)
    {
        return new ChunkReservation(uploadId, chunkId, key, Instant.now(), ChunkState.RESERVED);
    }

}
