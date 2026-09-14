package com.irs.mef.newsend.error;

import org.springframework.http.HttpStatus;

/** No active IRS MeF session. Nothing was sent; login and resend. */
public final class NewSendNotLoggedInException extends NewSendException {

    public NewSendNotLoggedInException() {
        super("No active IRS MeF session. POST /api/mef/auth/login first.");
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.UNAUTHORIZED;
    }

    @Override
    public String errorCode() {
        return "NEWSEND_NOT_LOGGED_IN";
    }

    @Override
    public boolean resubmitSafe() {
        return true;
    }
}
