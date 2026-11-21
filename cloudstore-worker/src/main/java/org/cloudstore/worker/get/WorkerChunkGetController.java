package org.cloudstore.worker.get;

import org.cloudstore.worker.api.constants.WorkerApiConstants;
import org.cloudstore.worker.reservation.ReservationResult;
import org.cloudstore.worker.reservation.WorkerReservation;
import org.cloudstore.worker.reservation.lease.ReservationLeaseService;
import org.cloudstore.worker.reservation.lease.ReservationLeaseService.LeaseRenewalHandle;
import org.cloudstore.worker.storage.WorkerChunkFileStorage;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.util.Optional;

/**
 * HTTP endpoint for streaming a reserved chunk from worker storage.
 */
@RestController
@RequestMapping(WorkerApiConstants.API_ROOT)
public class WorkerChunkGetController
{
    private final WorkerChunkFileStorage chunkFileStorage;
    private final WorkerReservation workerReservation;
    private final ReservationLeaseService leaseService;

    /**
     * Creates the endpoint with storage and reservation services.
     */
    public WorkerChunkGetController(
            WorkerChunkFileStorage chunkFileStorage,
            WorkerReservation workerReservation,
            ReservationLeaseService leaseService)
    {
        this.chunkFileStorage = chunkFileStorage;
        this.workerReservation = workerReservation;
        this.leaseService = leaseService;
    }

    /**
     * Checks the reservation and streams the stored chunk as octet-stream data.
     */
    @GetMapping(WorkerApiConstants.CHUNK_PATH)
    public ResponseEntity<StreamingResponseBody> get(
            @PathVariable String fileId,
            @PathVariable String chunkId,
            @RequestHeader(name = WorkerApiConstants.RESERVATION_KEY_HEADER, required = true) String key)
            throws IOException
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

        Optional<WorkerChunkFileStorage.ChunkReadHandle> chunk =
                chunkFileStorage.openForRead(fileId, chunkId);
        if (chunk.isEmpty())
        {
            return ResponseEntity.notFound().build();
        }

        WorkerChunkFileStorage.ChunkReadHandle readHandle = chunk.get();
        StreamingResponseBody responseBody = outputStream ->
        {
            LeaseRenewalHandle renewal;
            try
            {
                renewal = leaseService.startRenewal(fileId, chunkId, key, () -> closeReadHandle(readHandle));
            } catch (RuntimeException renewalFailure)
            {
                readHandle.close();
                throw renewalFailure;
            }
            try (readHandle; renewal)
            {
                readHandle.inputStream().transferTo(outputStream);
                leaseService.renewOrThrowIfInactive(fileId, chunkId, key);
                if (renewal.isLeaseLost())
                {
                    throw new IOException();
                }
            }
        };
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentLength(readHandle.fileSize());
        return new ResponseEntity<>(responseBody, headers, HttpStatus.OK);
    }

    /**
     * Closes the active file stream when reservation renewal fails.
     */
    private void closeReadHandle(WorkerChunkFileStorage.ChunkReadHandle readHandle)
    {
        try
        {
            readHandle.close();
        } catch (IOException ignored)
        {
            // The streaming callback will observe the closed stream and terminate the response.
        }
    }
}
