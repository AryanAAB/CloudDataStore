package org.cloudstore.worker.delete;

import org.cloudstore.worker.api.constants.WorkerApiConstants;
import org.cloudstore.worker.reservation.ReservationResult;
import org.cloudstore.worker.reservation.WorkerReservation;
import org.cloudstore.worker.storage.WorkerChunkFileStorage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * HTTP endpoint for deleting a chunk stored on this worker.
 */
@RestController
@RequestMapping(WorkerApiConstants.API_ROOT)
public class WorkerChunkDeleteController
{
    private final WorkerChunkFileStorage chunkFileStorage;
    private final WorkerReservation workerReservation;

    /**
     * Creates the endpoint with the worker's chunk storage operation.
     */
    public WorkerChunkDeleteController(
            WorkerChunkFileStorage chunkFileStorage,
            WorkerReservation workerReservation)
    {
        this.chunkFileStorage = chunkFileStorage;
        this.workerReservation = workerReservation;
    }

    /**
     * Deletes the stored chunk and returns 404 when it does not exist.
     */
    @DeleteMapping(WorkerApiConstants.CHUNK_PATH)
    public ResponseEntity<Void> delete(
            @PathVariable String fileId,
            @PathVariable String chunkId,
            @org.springframework.web.bind.annotation.RequestHeader(
                    name = WorkerApiConstants.RESERVATION_KEY_HEADER,
                    required = true) String key) throws IOException
    {
        ReservationResult reservation = workerReservation.reserve(chunkId, fileId, key);
        if (!reservation.success())
        {
            if (reservation.failureCategory() == ReservationResult.FailureCategory.INVALID_REQUEST)
            {
                return ResponseEntity.badRequest().build();
            }
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        if (!chunkFileStorage.delete(fileId, chunkId))
        {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }
}

