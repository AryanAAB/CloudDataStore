package org.cloudstore.controller.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an object or session is not found.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ObjectNotFoundException extends RuntimeException
{
    public ObjectNotFoundException(String message)
    {
        super(message);
    }
}

