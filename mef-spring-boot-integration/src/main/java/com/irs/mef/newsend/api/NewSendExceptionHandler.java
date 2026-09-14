package com.irs.mef.newsend.api;

import com.irs.mef.newsend.error.NewSendBusyException;
import com.irs.mef.newsend.error.NewSendDuplicateException;
import com.irs.mef.newsend.error.NewSendException;
import com.irs.mef.newsend.error.NewSendValidationException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/**
 * Scoped to the NewSend controller ONLY (assignableTypes) and ordered first, so the legacy
 * GlobalExceptionHandler — which maps every MefException to HTTP 500 — keeps its behavior for
 * legacy routes and nothing about legacy clients changes.
 */
@RestControllerAdvice(assignableTypes = NewSendSubmissionController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class NewSendExceptionHandler {

    @ExceptionHandler(NewSendException.class)
    public ResponseEntity<NewSendErrorResponse> handleNewSend(NewSendException ex) {
        HttpStatus status = ex.status();
        NewSendErrorResponse.NewSendErrorResponseBuilder body = NewSendErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .errorCode(ex.errorCode())
                .message(ex.getMessage())
                .resubmitSafe(ex.resubmitSafe());
        if (ex instanceof NewSendValidationException validation) {
            body.field(validation.field());
        }
        if (ex instanceof NewSendDuplicateException duplicate) {
            body.conflictingSubmissionIds(duplicate.conflictingSubmissionIds());
        }
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status);
        if (ex instanceof NewSendBusyException busy) {
            response.header("Retry-After", String.valueOf(busy.retryAfterSeconds()));
        }
        log.warn("NewSend request refused: {} {}", ex.errorCode(), ex.getMessage());
        return response.body(body.build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<NewSendErrorResponse> handleBeanValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("Request body validation failed");
        return badRequest("NEWSEND_VALIDATION", message);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<NewSendErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
        return badRequest("NEWSEND_VALIDATION",
                ex.getHeaderName() + " header is required. A stable key per filing makes retries safe.");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<NewSendErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        return badRequest("NEWSEND_VALIDATION", ex.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<NewSendErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        return badRequest("NEWSEND_VALIDATION", "Request body is not readable JSON: "
                + (ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage()));
    }

    /** Anything else from the NewSend controller is still a 500, but shaped like our error body. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<NewSendErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unexpected error on a NewSend endpoint", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(NewSendErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .errorCode("NEWSEND_INTERNAL")
                .message(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName())
                .build());
    }

    private ResponseEntity<NewSendErrorResponse> badRequest(String errorCode, String message) {
        return ResponseEntity.badRequest().body(NewSendErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .errorCode(errorCode)
                .message(message)
                .resubmitSafe(true)
                .build());
    }
}
