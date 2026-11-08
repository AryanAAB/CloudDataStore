package org.cloudstore.worker.reservation.cleaner;

import org.cloudstore.worker.data.reservation.ChunkRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for scheduled removal of expired worker records.
 */
@ExtendWith(MockitoExtension.class)
class WorkerReservationCleanerTest
{
    @Mock
    private ChunkRecordRepository chunkRecords;

    @Test
    void deletesRecordsExpiredByTheCurrentTime()
    {
        when(chunkRecords.deleteByExpiresAtLessThanEqual(any(Instant.class))).thenReturn(2L);
        WorkerReservationCleaner cleaner = new WorkerReservationCleaner(chunkRecords);

        long removedRecords = cleaner.cleanExpiredRecords();

        ArgumentCaptor<Instant> expirationTime = ArgumentCaptor.forClass(Instant.class);
        verify(chunkRecords).deleteByExpiresAtLessThanEqual(expirationTime.capture());
        assertThat(expirationTime.getValue()).isBeforeOrEqualTo(Instant.now());
        assertThat(removedRecords).isEqualTo(2L);
    }

    @Test
    void returnsZeroWhenNoRecordsHaveExpired()
    {
        when(chunkRecords.deleteByExpiresAtLessThanEqual(any(Instant.class))).thenReturn(0L);
        WorkerReservationCleaner cleaner = new WorkerReservationCleaner(chunkRecords);

        long removedRecords = cleaner.cleanExpiredRecords();

        assertThat(removedRecords).isZero();
    }
}
