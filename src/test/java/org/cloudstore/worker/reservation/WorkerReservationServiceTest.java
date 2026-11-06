package org.cloudstore.worker.reservation;

import org.cloudstore.worker.ChunkState;
import org.cloudstore.worker.Constants;
import org.cloudstore.worker.key.OpaqueStringReservationIdentifierValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the worker's reservation behavior.
 */
@ExtendWith(MockitoExtension.class)
class WorkerReservationServiceTest
{
    private static final String UPLOAD_ID = "upload-2026-alpha";
    private static final String CHUNK_ID = "chunk-part-A";

    @Mock
    private ChunkReservationRepository reservations;

    private WorkerReservationService service;

    @BeforeEach
    void setUp()
    {
        service = new WorkerReservationService(
                reservations,
                new OpaqueStringReservationIdentifierValidator());
    }

    @Test
    void createsReservationInReservedStateAndAllowsNullKey()
    {
        ChunkReservationId id = new ChunkReservationId(UPLOAD_ID, CHUNK_ID);
        when(reservations.findById(id)).thenReturn(Optional.empty());
        when(reservations.saveAndFlush(any(ChunkReservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResult result = service.reserve(UPLOAD_ID, CHUNK_ID, null);

        ArgumentCaptor<ChunkReservation> saved = ArgumentCaptor.forClass(ChunkReservation.class);
        verify(reservations).saveAndFlush(saved.capture());
        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo(Constants.RESERVATION_CREATED);
        assertThat(saved.getValue().getState()).isEqualTo(ChunkState.RESERVED);
        assertThat(saved.getValue().getReservationKey()).isNull();
        assertThat(saved.getValue().getUploadTime()).isNotNull();
    }

    @Test
    void rejectsInvalidIdsWithoutAccessingRepository()
    {
        ReservationResult result = service.reserve(" ", CHUNK_ID, null);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo(String.format(
                Constants.INVALID_RESERVATION_IDS,
                Constants.MAX_RESERVATION_ID_LENGTH));
        verifyNoInteractions(reservations);
    }

    @Test
    void rejectsBlankReservationKey()
    {
        ReservationResult result = service.reserve(UPLOAD_ID, CHUNK_ID, "   ");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo(String.format(
                Constants.INVALID_RESERVATION_KEY,
                Constants.MAX_RESERVATION_KEY_LENGTH));
        verifyNoInteractions(reservations);
    }

    @Test
    void returnsSuccessForAnIdenticalExistingReservation()
    {
        ChunkReservation existing = reservation("shared-key");
        when(reservations.findById(new ChunkReservationId(UPLOAD_ID, CHUNK_ID)))
                .thenReturn(Optional.of(existing));

        ReservationResult result = service.reserve(UPLOAD_ID, CHUNK_ID, "shared-key");

        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo(Constants.RESERVATION_ALREADY_EXISTS);
        verify(reservations, never()).saveAndFlush(any(ChunkReservation.class));
    }

    @Test
    void rejectsAnExistingReservationWithADifferentKey()
    {
        ChunkReservation existing = reservation("old-key");
        when(reservations.findById(new ChunkReservationId(UPLOAD_ID, CHUNK_ID)))
                .thenReturn(Optional.of(existing));

        ReservationResult result = service.reserve(UPLOAD_ID, CHUNK_ID, "new-key");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo(Constants.RESERVATION_KEY_MISMATCH);
        verify(reservations, never()).saveAndFlush(any(ChunkReservation.class));
    }

    @Test
    void treatsConcurrentIdenticalInsertAsSuccess()
    {
        ChunkReservation existing = reservation("shared-key");
        ChunkReservationId id = new ChunkReservationId(UPLOAD_ID, CHUNK_ID);
        when(reservations.findById(id)).thenReturn(Optional.empty(), Optional.of(existing));
        when(reservations.saveAndFlush(any(ChunkReservation.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate reservation"));

        ReservationResult result = service.reserve(UPLOAD_ID, CHUNK_ID, "shared-key");

        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo(Constants.RESERVATION_ALREADY_EXISTS);
    }

    private static ChunkReservation reservation(String key)
    {
        return new ChunkReservation(UPLOAD_ID, CHUNK_ID, key, Instant.now(), ChunkState.RESERVED);
    }
}
