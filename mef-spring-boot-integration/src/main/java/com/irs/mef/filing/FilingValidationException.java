package com.irs.mef.filing;

import org.springframework.http.HttpStatus;

/** A request was rejected before anything was composed or sent. Always safe to correct and resend. */
public final class FilingValidationException extends FilingException {

    private final String field;

    FilingValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.BAD_REQUEST;
    }

    @Override
    public String errorCode() {
        return "NEWSEND_VALIDATION";
    }

    @Override
    public boolean resubmitSafe() {
        return true;
    }
}
