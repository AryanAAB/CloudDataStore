package org.cloudstore.worker.reservation.cleaner;

import org.cloudstore.worker.data.reservation.ChunkRecordRepository;
import org.cloudstore.worker.reservation.cleaner.constants.WorkerReservationCleanerLogMessages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Periodically removes expired records from the worker chunk table.
 */
@Service
public class WorkerReservationCleaner
{
    private static final Logger LOGGER = LoggerFactory.getLogger(WorkerReservationCleaner.class);

    private final ChunkRecordRepository chunkRecords;

    /**
     * Creates the cleaner with the repository it will use to remove expired records.
     *
     * @param chunkRecords repository for worker chunk records
     */
    public WorkerReservationCleaner(ChunkRecordRepository chunkRecords)
    {
        this.chunkRecords = chunkRecords;
    }

    /**
     * Deletes records that expired by the current time. Spring runs this method at the
     * configured fixed delay between cleanup passes.
     *
     * @return number of expired records deleted
     */
    @Transactional
    public long cleanExpiredRecords()
    {
        long removedRecords = chunkRecords.deleteByExpiresAtLessThanEqual(Instant.now());
        LOGGER.info(WorkerReservationCleanerLogMessages.CLEANUP_COMPLETED, removedRecords);
        return removedRecords;
    }
}
