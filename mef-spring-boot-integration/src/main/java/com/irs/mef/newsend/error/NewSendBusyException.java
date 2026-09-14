package com.irs.mef.newsend.error;

import org.springframework.http.HttpStatus;

/** Another transmit holds the single IRS session. Nothing was sent; retry after the hinted delay. */
public final class NewSendBusyException extends NewSendException {

    private final long retryAfterSeconds;

    public NewSendBusyException(long retryAfterSeconds) {
        super("The IRS MeF session is busy with another transmission. Retry after " + retryAfterSeconds + "s.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.SERVICE_UNAVAILABLE;
    }

    @Override
    public String errorCode() {
        return "NEWSEND_SESSION_BUSY";
    }

    @Override
    public boolean resubmitSafe() {
        return true;
    }
}
