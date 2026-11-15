package org.cloudstore.controller.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a client upload exceeds its allocated byte budget or chunk budget.
 */
@ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
public class QuotaExceededException extends RuntimeException
{
    public QuotaExceededException(String message)
    {
        super(message);
    }
}

