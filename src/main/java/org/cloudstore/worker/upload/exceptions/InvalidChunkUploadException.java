package org.cloudstore.worker.upload.exceptions;

import org.cloudstore.worker.upload.constants.WorkerChunkUploadMessages;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Indicates that a chunk upload contains invalid identifiers or a malformed key.
 */
@ResponseStatus(code = HttpStatus.BAD_REQUEST, reason = WorkerChunkUploadMessages.INVALID_REQUEST)
public class InvalidChunkUploadException extends RuntimeException
{
}
