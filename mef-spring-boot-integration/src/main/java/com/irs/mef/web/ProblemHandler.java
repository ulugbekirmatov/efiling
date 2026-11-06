package com.irs.mef.web;

import com.irs.mef.filing.FilingBusyException;
import com.irs.mef.filing.FilingDuplicateException;
import com.irs.mef.filing.FilingException;
import com.irs.mef.filing.FilingValidationException;
import com.irs.mef.filing.IrsLoginException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

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
        log.warn("Filing request refused: {} {}", ex.errorCode(), ex.getMessage());
        return response.contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body.build());
    }

    @ExceptionHandler(IrsLoginException.class)
    public ResponseEntity<ProblemResponse> handleLogin(IrsLoginException ex) {
        log.error("IRS login failed", ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ProblemResponse.builder()
                        .timestamp(Instant.now())
                        .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                        .errorCode("IRS_LOGIN_FAILED")
                        .message("IRS login failed; see server logs")
                        .build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemResponse> handleBeanValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("Request body validation failed");
        return badRequest("NEWSEND_VALIDATION", message);
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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemResponse> handleUnexpected(Exception ex) {
        log.error("Unexpected error on a filing endpoint", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ProblemResponse.builder()
                        .timestamp(Instant.now())
                        .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                        .errorCode("FILING_INTERNAL")
                        .message("Internal error; see server logs")
                        .build());
    }

    private ResponseEntity<ProblemResponse> badRequest(String errorCode, String message) {
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_PROBLEM_JSON).body(ProblemResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .errorCode(errorCode)
                .message(message)
                .resubmitSafe(true)
                .build());
    }
}
