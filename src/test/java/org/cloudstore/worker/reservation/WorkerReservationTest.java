package org.cloudstore.worker.reservation;

import org.cloudstore.worker.data.ChunkRecord;
import org.cloudstore.worker.data.ChunkRecordId;
import org.cloudstore.worker.data.ChunkRecordRepository;
import org.cloudstore.worker.reservation.constants.WorkerReservationConstants;
import org.cloudstore.worker.reservation.constants.WorkerReservationMessages;
import org.cloudstore.worker.reservation.renewal.AfterExpirationKeyRenewalStrategy;
import org.cloudstore.worker.reservation.renewal.AlwaysAcceptKeyRenewalStrategy;
import org.cloudstore.worker.validation.constants.ValidationConstants;
import org.cloudstore.worker.validation.identifier.OpaqueStringIdentifierValidator;
import org.cloudstore.worker.validation.key.OptionalReservationKeyValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for worker reservation persistence and expiration behavior.
 */
@ExtendWith(MockitoExtension.class)
public class WorkerReservationTest
{
    private static final String CHUNK_ID = "chunk-reservation-test";
    private static final String UPLOAD_ID = "upload-reservation-test";
    private static final String RESERVATION_KEY = "reservation-key-test";

    @Mock
    private ChunkRecordRepository chunkRecords;

    private WorkerReservation reservationThread;

    @BeforeEach
    void setUp()
    {
        reservationThread = new WorkerReservation(
                chunkRecords,
                new OpaqueStringIdentifierValidator(),
                new OptionalReservationKeyValidator(),
                new AfterExpirationKeyRenewalStrategy());
    }

    @Test
    void createsReservationWithCompositeIdKeyAndExpiration()
    {
        ChunkRecordId id = new ChunkRecordId(UPLOAD_ID, CHUNK_ID);
        when(chunkRecords.findById(id)).thenReturn(Optional.empty());
        when(chunkRecords.saveAndFlush(any(ChunkRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        Instant beforeRequest = Instant.now();

        ReservationResult result = reservationThread.reserve(CHUNK_ID, UPLOAD_ID, RESERVATION_KEY);

        ArgumentCaptor<ChunkRecord> savedReservation =
                ArgumentCaptor.forClass(ChunkRecord.class);
        verify(chunkRecords).saveAndFlush(savedReservation.capture());
        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo(WorkerReservationMessages.CREATED);
        assertThat(savedReservation.getValue().getChunkId()).isEqualTo(CHUNK_ID);
        assertThat(savedReservation.getValue().getUploadId()).isEqualTo(UPLOAD_ID);
        assertThat(savedReservation.getValue().getReservationKey()).isEqualTo(RESERVATION_KEY);
        assertThat(savedReservation.getValue().getExpiresAt())
                .isAfter(beforeRequest.plusMillis(
                        WorkerReservationConstants.RESERVATION_TTL_MILLIS - 1_000L));
    }

    @Test
    void permitsNullReservationKey()
    {
        when(chunkRecords.findById(new ChunkRecordId(UPLOAD_ID, CHUNK_ID)))
                .thenReturn(Optional.empty());
        when(chunkRecords.saveAndFlush(any(ChunkRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResult result = reservationThread.reserve(CHUNK_ID, UPLOAD_ID, null);

        assertThat(result.success()).isTrue();
    }

    @Test
    void renewsAnActiveReservationWhenTheKeyMatches()
    {
        ChunkRecord existing = reservation(
                RESERVATION_KEY,
                Instant.now().plusMillis(WorkerReservationConstants.RESERVATION_TTL_MILLIS));
        Instant previousExpiry = existing.getExpiresAt();
        when(chunkRecords.findById(new ChunkRecordId(UPLOAD_ID, CHUNK_ID)))
                .thenReturn(Optional.of(existing));
        when(chunkRecords.saveAndFlush(existing)).thenReturn(existing);

        ReservationResult result = reservationThread.reserve(CHUNK_ID, UPLOAD_ID, RESERVATION_KEY);

        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo(WorkerReservationMessages.RENEWED);
        assertThat(existing.getExpiresAt()).isAfter(previousExpiry);
        verify(chunkRecords).saveAndFlush(existing);
    }

    @Test
    void rejectsDifferentKeyWhileReservationIsActive()
    {
        Instant previousExpiry = Instant.now().plusMillis(
                WorkerReservationConstants.RESERVATION_TTL_MILLIS);
        ChunkRecord existing = reservation(RESERVATION_KEY, previousExpiry);
        when(chunkRecords.findById(new ChunkRecordId(UPLOAD_ID, CHUNK_ID)))
                .thenReturn(Optional.of(existing));

        ReservationResult result = reservationThread.reserve(CHUNK_ID, UPLOAD_ID, "other-key");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo(WorkerReservationMessages.KEY_CONFLICT);
        assertThat(existing.getReservationKey()).isEqualTo(RESERVATION_KEY);
        assertThat(existing.getExpiresAt()).isEqualTo(previousExpiry);
    }

    @Test
    void alwaysAcceptStrategyReplacesKeyOnAnActiveReservation()
    {
        Instant previousExpiry = Instant.now().plusMillis(
                WorkerReservationConstants.RESERVATION_TTL_MILLIS);
        ChunkRecord existing = reservation(RESERVATION_KEY, previousExpiry);
        when(chunkRecords.findById(new ChunkRecordId(UPLOAD_ID, CHUNK_ID)))
                .thenReturn(Optional.of(existing));
        when(chunkRecords.saveAndFlush(existing)).thenReturn(existing);
        WorkerReservation alwaysAcceptWorkerReservation = new WorkerReservation(
                chunkRecords,
                new OpaqueStringIdentifierValidator(),
                new OptionalReservationKeyValidator(),
                new AlwaysAcceptKeyRenewalStrategy());

        ReservationResult result = alwaysAcceptWorkerReservation.reserve(
                CHUNK_ID,
                UPLOAD_ID,
                "replacement-key");

        assertThat(result.success()).isTrue();
        assertThat(existing.getReservationKey()).isEqualTo("replacement-key");
        assertThat(existing.getExpiresAt()).isAfter(previousExpiry);
    }

    @Test
    void reusesExpiredReservationWithANewKey()
    {
        ChunkRecord expired = reservation(RESERVATION_KEY, Instant.now().minusSeconds(1));
        when(chunkRecords.findById(new ChunkRecordId(UPLOAD_ID, CHUNK_ID)))
                .thenReturn(Optional.of(expired));
        when(chunkRecords.saveAndFlush(expired)).thenReturn(expired);

        ReservationResult result = reservationThread.reserve(CHUNK_ID, UPLOAD_ID, "new-key");

        assertThat(result.success()).isTrue();
        assertThat(expired.getReservationKey()).isEqualTo("new-key");
        assertThat(expired.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void rejectsInvalidIdentifiersWithoutUsingRepository()
    {
        ReservationResult result = reservationThread.reserve(" ", UPLOAD_ID, RESERVATION_KEY);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo(String.format(
                WorkerReservationMessages.INVALID_IDENTIFIERS,
                ValidationConstants.MAX_IDENTIFIER_LENGTH));
        verifyNoInteractions(chunkRecords);
    }

    private static ChunkRecord reservation(String key, Instant expiresAt)
    {
        return new ChunkRecord(UPLOAD_ID, CHUNK_ID, key, expiresAt);
    }
}
