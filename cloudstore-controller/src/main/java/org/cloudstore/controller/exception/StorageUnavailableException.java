package org.cloudstore.controller.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when cluster storage capacity or live worker availability is insufficient.
 */
@ResponseStatus(HttpStatus.INSUFFICIENT_STORAGE)
public class StorageUnavailableException extends RuntimeException
{
    public StorageUnavailableException(String message)
    {
        super(message);
    }
}

