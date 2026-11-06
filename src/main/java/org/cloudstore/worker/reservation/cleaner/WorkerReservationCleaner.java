package org.cloudstore.worker.reservation.cleaner;

import org.cloudstore.worker.ChunkState;
import org.cloudstore.worker.Constants;
import org.cloudstore.worker.reservation.ChunkReservation;
import org.cloudstore.worker.reservation.ChunkReservationRepository;
import org.cloudstore.worker.reservation.ReservationLogMessages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Expires reservations that remain unused beyond the configured timeout.
 */
@Service
public class WorkerReservationCleaner
{
    private static final Logger LOGGER = LoggerFactory.getLogger(WorkerReservationCleaner.class);

    private final ChunkReservationRepository reservations;

    /**
     * Creates the cleaner with the repository used to inspect and update reservations.
     *
     * @param reservations repository for reservation records
     */
    public WorkerReservationCleaner(ChunkReservationRepository reservations)
    {
        this.reservations = reservations;
    }

    /**
     * Finds reserved chunks that have exceeded the timeout and marks them expired.
     * The worker repeats this pass at the configured cleanup interval.
     */
    @Scheduled(fixedDelay = Constants.RESERVATION_CLEANER_INTERVAL_MILLIS)
    @Transactional
    public void cleanExpiredReservations()
    {
        LOGGER.info(ReservationLogMessages.Cleaner.CLEANUP_STARTED);
        Instant currentTime = Instant.now();
        List<ChunkReservation> reservedChunks = reservations.findAllByState(ChunkState.RESERVED);
        List<ChunkReservation> expiredChunks = new ArrayList<>();

        for (ChunkReservation reservation : reservedChunks)
        {
            Duration elapsed = Duration.between(reservation.getUploadTime(), currentTime);
            if (elapsed.toMillis() >= Constants.RESERVATION_TIMEOUT_MILLIS)
            {
                reservation.markExpired();
                expiredChunks.add(reservation);
                LOGGER.info(ReservationLogMessages.Cleaner.RESERVATION_EXPIRED,
                        reservation.getUploadId(),
                        reservation.getChunkId());
            }
        }

        if (!expiredChunks.isEmpty())
        {
            reservations.saveAll(expiredChunks);
        }
        LOGGER.info(ReservationLogMessages.Cleaner.CLEANUP_COMPLETED, expiredChunks.size());
    }
}
