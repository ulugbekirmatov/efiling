package com.irs.mef.newsend.error;

import org.springframework.http.HttpStatus;

/** The server side is misconfigured (e.g. MEF_EFIN missing or malformed). Nothing was sent. */
public final class NewSendConfigurationException extends NewSendException {

    public NewSendConfigurationException(String message) {
        super(message);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    @Override
    public String errorCode() {
        return "NEWSEND_CONFIGURATION";
    }

    @Override
    public boolean resubmitSafe() {
        return true;
    }
}
