package org.cloudstore.worker.reservation;

import org.cloudstore.worker.data.reservation.ChunkRecord;
import org.cloudstore.worker.data.reservation.ChunkRecordId;
import org.cloudstore.worker.data.reservation.ChunkRecordRepository;
import org.cloudstore.worker.reservation.constants.WorkerReservationMessages;
import org.cloudstore.worker.reservation.constants.WorkerReservationProperties;
import org.cloudstore.worker.reservation.renewal.strategies.AfterExpirationKeyRenewalStrategy;
import org.cloudstore.worker.reservation.renewal.strategies.AlwaysAcceptKeyRenewalStrategy;
import org.cloudstore.worker.validation.constants.WorkerValidationProperties;
import org.cloudstore.worker.validation.identifier.OpaqueStringIdentifierValidator;
import org.cloudstore.worker.validation.key.OptionalReservationKeyValidator;
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
 * Unit tests for worker reservation persistence and expiration behavior.
 */
@ExtendWith(MockitoExtension.class)
public class WorkerReservationTest
{
    private static final String CHUNK_ID = "chunk-reservation-test";
    private static final String FILE_ID = "file-reservation-test";
    private static final String RESERVATION_KEY = "reservation-key-test";

    @Mock
    private ChunkRecordRepository chunkRecords;

    private WorkerReservation reservationThread;
    private WorkerReservationProperties reservationProperties;
    private WorkerValidationProperties validationProperties;

    @BeforeEach
    void setUp()
    {
        reservationProperties = new WorkerReservationProperties();
        reservationProperties.setTtlSeconds(60L);
        validationProperties = new WorkerValidationProperties();
        validationProperties.setMaxIdentifierLength(255);
        validationProperties.setMaxReservationKeyLength(128);
        reservationThread = new WorkerReservation(
                chunkRecords,
                new OpaqueStringIdentifierValidator(validationProperties),
                new OptionalReservationKeyValidator(validationProperties),
                new AfterExpirationKeyRenewalStrategy(),
                reservationProperties,
                validationProperties);
    }

    @Test
    void createsReservationWithCompositeIdKeyAndExpiration()
    {
        ChunkRecordId id = new ChunkRecordId(FILE_ID, CHUNK_ID);
        when(chunkRecords.findById(id)).thenReturn(Optional.empty());
        when(chunkRecords.saveAndFlush(any(ChunkRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        Instant beforeRequest = Instant.now();

        ReservationResult result = reservationThread.reserve(CHUNK_ID, FILE_ID, RESERVATION_KEY);

        ArgumentCaptor<ChunkRecord> savedReservation =
                ArgumentCaptor.forClass(ChunkRecord.class);
        verify(chunkRecords).saveAndFlush(savedReservation.capture());
        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo(WorkerReservationMessages.CREATED);
        assertThat(savedReservation.getValue().getChunkId()).isEqualTo(CHUNK_ID);
        assertThat(savedReservation.getValue().getFileId()).isEqualTo(FILE_ID);
        assertThat(savedReservation.getValue().getReservationKey()).isEqualTo(RESERVATION_KEY);
        assertThat(savedReservation.getValue().getExpiresAt())
                .isAfter(beforeRequest.plusSeconds(reservationProperties.getTtlSeconds() - 1L));
    }

    @Test
    void permitsNullReservationKey()
    {
        when(chunkRecords.findById(new ChunkRecordId(FILE_ID, CHUNK_ID)))
                .thenReturn(Optional.empty());
        when(chunkRecords.saveAndFlush(any(ChunkRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResult result = reservationThread.reserve(CHUNK_ID, FILE_ID, null);

        assertThat(result.success()).isTrue();
    }

    @Test
    void renewsAnActiveReservationWhenTheKeyMatches()
    {
        ChunkRecord existing = reservation(
                RESERVATION_KEY,
                Instant.now().plusSeconds(reservationProperties.getTtlSeconds()));
        Instant previousExpiry = existing.getExpiresAt();
        when(chunkRecords.findById(new ChunkRecordId(FILE_ID, CHUNK_ID)))
                .thenReturn(Optional.of(existing));
        when(chunkRecords.saveAndFlush(existing)).thenReturn(existing);

        ReservationResult result = reservationThread.reserve(CHUNK_ID, FILE_ID, RESERVATION_KEY);

        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo(WorkerReservationMessages.RENEWED);
        assertThat(existing.getExpiresAt()).isAfter(previousExpiry);
        verify(chunkRecords).saveAndFlush(existing);
    }

    @Test
    void rejectsDifferentKeyWhileReservationIsActive()
    {
        Instant previousExpiry = Instant.now().plusSeconds(reservationProperties.getTtlSeconds());
        ChunkRecord existing = reservation(RESERVATION_KEY, previousExpiry);
        when(chunkRecords.findById(new ChunkRecordId(FILE_ID, CHUNK_ID)))
                .thenReturn(Optional.of(existing));

        ReservationResult result = reservationThread.reserve(CHUNK_ID, FILE_ID, "other-key");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo(WorkerReservationMessages.KEY_CONFLICT);
        assertThat(existing.getReservationKey()).isEqualTo(RESERVATION_KEY);
        assertThat(existing.getExpiresAt()).isEqualTo(previousExpiry);
    }

    @Test
    void alwaysAcceptStrategyReplacesKeyOnAnActiveReservation()
    {
        Instant previousExpiry = Instant.now().plusSeconds(reservationProperties.getTtlSeconds());
        ChunkRecord existing = reservation(RESERVATION_KEY, previousExpiry);
        when(chunkRecords.findById(new ChunkRecordId(FILE_ID, CHUNK_ID)))
                .thenReturn(Optional.of(existing));
        when(chunkRecords.saveAndFlush(existing)).thenReturn(existing);
        WorkerReservation alwaysAcceptWorkerReservation = new WorkerReservation(
                chunkRecords,
                new OpaqueStringIdentifierValidator(validationProperties),
                new OptionalReservationKeyValidator(validationProperties),
                new AlwaysAcceptKeyRenewalStrategy(),
                reservationProperties,
                validationProperties);

        ReservationResult result = alwaysAcceptWorkerReservation.reserve(
                CHUNK_ID,
                FILE_ID,
                "replacement-key");

        assertThat(result.success()).isTrue();
        assertThat(existing.getReservationKey()).isEqualTo("replacement-key");
        assertThat(existing.getExpiresAt()).isAfter(previousExpiry);
    }

    @Test
    void reusesExpiredReservationWithANewKey()
    {
        ChunkRecord expired = reservation(RESERVATION_KEY, Instant.now().minusSeconds(1));
        when(chunkRecords.findById(new ChunkRecordId(FILE_ID, CHUNK_ID)))
                .thenReturn(Optional.of(expired));
        when(chunkRecords.saveAndFlush(expired)).thenReturn(expired);

        ReservationResult result = reservationThread.reserve(CHUNK_ID, FILE_ID, "new-key");

        assertThat(result.success()).isTrue();
        assertThat(expired.getReservationKey()).isEqualTo("new-key");
        assertThat(expired.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void rejectsInvalidIdentifiersWithoutUsingRepository()
    {
        ReservationResult result = reservationThread.reserve(" ", FILE_ID, RESERVATION_KEY);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo(String.format(
                WorkerReservationMessages.INVALID_IDENTIFIERS,
                validationProperties.getMaxIdentifierLength()));
        verifyNoInteractions(chunkRecords);
    }

    @Test
    void resolvesConcurrentInsertWhenWinningReservationUsesTheSameKey()
    {
        ChunkRecordId id = new ChunkRecordId(FILE_ID, CHUNK_ID);
        ChunkRecord winner = reservation(
                RESERVATION_KEY,
                Instant.now().plusSeconds(reservationProperties.getTtlSeconds()));
        when(chunkRecords.findById(id)).thenReturn(Optional.empty(), Optional.of(winner));
        doThrow(new DataIntegrityViolationException("duplicate reservation"))
                .doAnswer(invocation -> invocation.getArgument(0))
                .when(chunkRecords).saveAndFlush(any(ChunkRecord.class));

        ReservationResult result = reservationThread.reserve(CHUNK_ID, FILE_ID, RESERVATION_KEY);

        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo(WorkerReservationMessages.RENEWED);
        verify(chunkRecords, times(2)).saveAndFlush(any(ChunkRecord.class));
    }

    @Test
    void rejectsConcurrentInsertWhenWinningReservationHasAnotherActiveKey()
    {
        ChunkRecordId id = new ChunkRecordId(FILE_ID, CHUNK_ID);
        ChunkRecord winner = reservation(
                RESERVATION_KEY,
                Instant.now().plusSeconds(reservationProperties.getTtlSeconds()));
        when(chunkRecords.findById(id)).thenReturn(Optional.empty(), Optional.of(winner));
        doThrow(new DataIntegrityViolationException("duplicate reservation"))
                .when(chunkRecords).saveAndFlush(any(ChunkRecord.class));

        ReservationResult result = reservationThread.reserve(CHUNK_ID, FILE_ID, "competing-key");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo(WorkerReservationMessages.KEY_CONFLICT);
        verify(chunkRecords).saveAndFlush(any(ChunkRecord.class));
    }

    private static ChunkRecord reservation(String key, Instant expiresAt)
    {
        return new ChunkRecord(FILE_ID, CHUNK_ID, key, expiresAt);
    }
}
