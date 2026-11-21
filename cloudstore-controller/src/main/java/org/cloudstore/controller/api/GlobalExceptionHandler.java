package org.cloudstore.controller.api;

import jakarta.servlet.http.HttpServletRequest;
import org.cloudstore.controller.dto.ErrorResponse;
import org.cloudstore.controller.exception.ObjectNotFoundException;
import org.cloudstore.controller.exception.QuotaExceededException;
import org.cloudstore.controller.exception.StorageUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/**
 * Intercepts uncaught exceptions and produces meaningful JSON error messages
 * in strict compliance with Section 5.7 of the specification.
 */
@RestControllerAdvice
public class GlobalExceptionHandler
{
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ObjectNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ObjectNotFoundException e, HttpServletRequest request)
    {
        return buildErrorResponse(HttpStatus.NOT_FOUND, e.getMessage(), request);
    }

    @ExceptionHandler(QuotaExceededException.class)
    public ResponseEntity<ErrorResponse> handleQuotaExceeded(QuotaExceededException e, HttpServletRequest request)
    {
        return buildErrorResponse(HttpStatus.PAYLOAD_TOO_LARGE, e.getMessage(), request);
    }

    @ExceptionHandler(StorageUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleStorageUnavailable(StorageUnavailableException e, HttpServletRequest request)
    {
        return buildErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException e, HttpServletRequest request)
    {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, e.getMessage(), request);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleConflict(IllegalStateException e, HttpServletRequest request)
    {
        return buildErrorResponse(HttpStatus.CONFLICT, e.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception e, HttpServletRequest request)
    {
        LOGGER.error("Unhandled internal server error on path {}", request.getRequestURI(), e);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error: " + e.getMessage(), request);
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(HttpStatus status, String message, HttpServletRequest request)
    {
        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}

