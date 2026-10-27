package com.irs.mef.web;

import com.irs.mef.filing.FilingBusyException;
import com.irs.mef.filing.FilingDuplicateException;
import com.irs.mef.filing.FilingException;
import com.irs.mef.filing.FilingValidationException;
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

/** Scoped to the NewSend controller only. */
@RestControllerAdvice(assignableTypes = FilingController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class ProblemHandler {

    @ExceptionHandler(FilingException.class)
    public ResponseEntity<ProblemResponse> handleNewSend(FilingException ex) {
        HttpStatus status = ex.status();
        ProblemResponse.ProblemResponseBuilder body = ProblemResponse.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .errorCode(ex.errorCode())
                .message(ex.getMessage())
                .resubmitSafe(ex.resubmitSafe());
        if (ex instanceof FilingValidationException validation) {
            body.field(validation.field());
        }
        if (ex instanceof FilingDuplicateException duplicate) {
            body.conflictingSubmissionIds(duplicate.conflictingSubmissionIds());
        }
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status);
        if (ex instanceof FilingBusyException busy) {
            response.header("Retry-After", String.valueOf(busy.retryAfterSeconds()));
        }
        log.warn("NewSend request refused: {} {}", ex.errorCode(), ex.getMessage());
        return response.body(body.build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemResponse> handleBeanValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("Request body validation failed");
        return badRequest("NEWSEND_VALIDATION", message);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ProblemResponse> handleMissingHeader(MissingRequestHeaderException ex) {
        return badRequest("NEWSEND_VALIDATION",
                ex.getHeaderName() + " header is required. A stable key per filing makes retries safe.");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemResponse> handleConstraintViolation(ConstraintViolationException ex) {
        return badRequest("NEWSEND_VALIDATION", ex.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        return badRequest("NEWSEND_VALIDATION", "Request body is not readable JSON: "
                + (ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage()));
    }

    /** Anything else from the NewSend controller is still a 500, but shaped like our error body. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemResponse> handleUnexpected(Exception ex) {
        log.error("Unexpected error on a NewSend endpoint", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ProblemResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .errorCode("NEWSEND_INTERNAL")
                .message(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName())
                .build());
    }

    private ResponseEntity<ProblemResponse> badRequest(String errorCode, String message) {
        return ResponseEntity.badRequest().body(ProblemResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .errorCode(errorCode)
                .message(message)
                .resubmitSafe(true)
                .build());
    }
}
