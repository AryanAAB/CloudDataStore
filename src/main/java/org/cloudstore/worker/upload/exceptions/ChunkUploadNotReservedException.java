package org.cloudstore.worker.upload.exceptions;

import org.cloudstore.worker.upload.constants.WorkerChunkUploadMessages;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Indicates that no matching active reservation exists for the requested chunk.
 */
@ResponseStatus(code = HttpStatus.CONFLICT, reason = WorkerChunkUploadMessages.RESERVATION_REQUIRED)
public class ChunkUploadNotReservedException extends RuntimeException
{
}
