package com.cobre.notifications.adapter.in.web;

import com.cobre.notifications.application.exception.ForbiddenOperationException;
import com.cobre.notifications.application.exception.InvalidRequestException;
import com.cobre.notifications.application.exception.ResourceNotFoundException;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidRequestException.class)
    ResponseEntity<ApiResponses.ErrorEnvelope> invalidRequest(
            InvalidRequestException exception,
            HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "bad_request", exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiResponses.ErrorEnvelope> typeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "bad_request", "Request parameter is invalid", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponses.ErrorEnvelope> validation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "bad_request", "Request body is invalid", request);
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    ResponseEntity<ApiResponses.ErrorEnvelope> forbidden(
            ForbiddenOperationException exception,
            HttpServletRequest request) {
        return error(HttpStatus.FORBIDDEN, "forbidden", exception.getMessage(), request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiResponses.ErrorEnvelope> notFound(
            ResourceNotFoundException exception,
            HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "not_found", exception.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponses.ErrorEnvelope> unexpected(
            Exception exception,
            HttpServletRequest request) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "Unexpected error", request);
    }

    private static ResponseEntity<ApiResponses.ErrorEnvelope> error(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request) {
        String correlationId = CorrelationIdFilter.current(request);
        return ResponseEntity.status(status)
                .body(new ApiResponses.ErrorEnvelope(
                        new ApiResponses.ErrorResponse(code, message, correlationId)));
    }
}
