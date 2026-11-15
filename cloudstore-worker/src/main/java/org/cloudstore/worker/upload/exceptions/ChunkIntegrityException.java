package org.cloudstore.worker.upload.exceptions;

import org.cloudstore.worker.upload.constants.WorkerChunkUploadMessages;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Indicates that uploaded chunk bytes do not match the supplied size or checksum.
 */
@ResponseStatus(code = HttpStatus.UNPROCESSABLE_CONTENT, reason = WorkerChunkUploadMessages.CHUNK_INTEGRITY_MISMATCH)
public class ChunkIntegrityException extends RuntimeException
{
}
