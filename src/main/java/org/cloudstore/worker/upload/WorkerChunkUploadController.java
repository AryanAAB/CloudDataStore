package org.cloudstore.worker.upload;

import jakarta.servlet.http.HttpServletRequest;
import org.cloudstore.worker.api.constants.WorkerApiConstants;
import org.cloudstore.worker.upload.constants.WorkerChunkUploadConstants;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

/**
 * HTTP endpoint for streaming one reserved chunk to a worker.
 */
@RestController
@RequestMapping(WorkerApiConstants.API_ROOT)
public class WorkerChunkUploadController
{
    private final WorkerChunkUpload chunkUpload;

    /**
     * Creates the endpoint with the worker chunk upload operation.
     */
    public WorkerChunkUploadController(WorkerChunkUpload chunkUpload)
    {
        this.chunkUpload = chunkUpload;
    }

    /**
     * Receives a raw chunk body and streams it to worker storage.
     */
    @PutMapping(
            path = WorkerChunkUploadConstants.CHUNK_PATH,
            consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<Long> upload(
            @PathVariable String fileId,
            @PathVariable String chunkId,
            @RequestHeader(name = WorkerApiConstants.RESERVATION_KEY_HEADER, required = false) String key,
            @RequestHeader(name = WorkerChunkUploadConstants.EXPECTED_CHUNK_SIZE_HEADER) long expectedSize,
            @RequestHeader(name = WorkerChunkUploadConstants.EXPECTED_CHUNK_SHA_256_HEADER) String expectedChecksum,
            HttpServletRequest request) throws IOException
    {
        long storedBytes = chunkUpload.upload(
                fileId, chunkId, key, expectedSize, expectedChecksum, request.getInputStream());
        return ResponseEntity.ok(storedBytes);
    }
}
