package com.irs.mef.newsend.error;

import org.springframework.http.HttpStatus;

/** A request was rejected before anything was composed or sent. Always safe to correct and resend. */
public final class NewSendValidationException extends NewSendException {

    private final String field;

    public NewSendValidationException(String field, String message) {
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
