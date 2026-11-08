package org.cloudstore.worker.reservation;

import org.cloudstore.worker.api.constants.WorkerApiConstants;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * HTTP endpoint used by the master to reserve a chunk on this worker.
 */
@RestController
@RequestMapping(WorkerApiConstants.API_ROOT)
public class WorkerReservationController
{
    private final WorkerReservation workerReservation;

    /**
     * Creates the endpoint with the reservation operation.
     */
    public WorkerReservationController(WorkerReservation workerReservation)
    {
        this.workerReservation = workerReservation;
    }

    /**
     * Creates or renews the reservation for the requested file and chunk.
     */
    @PutMapping(WorkerApiConstants.CHUNK_RESERVATION_PATH)
    public ResponseEntity<ReservationResult> reserve(
            @PathVariable String fileId,
            @PathVariable String chunkId,
            @RequestHeader(
                    name = WorkerApiConstants.RESERVATION_KEY_HEADER,
                    required = false) String key)
    {
        ReservationResult result = workerReservation.reserve(chunkId, fileId, key);
        if (result.success())
        {
            return ResponseEntity.ok(result);
        }
        if (result.failureCategory() == ReservationResult.FailureCategory.INVALID_REQUEST)
        {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(result);
    }
}
