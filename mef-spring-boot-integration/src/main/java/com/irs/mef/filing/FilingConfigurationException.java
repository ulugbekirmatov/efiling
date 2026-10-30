package com.irs.mef.filing;

import org.springframework.http.HttpStatus;

/** The server side is misconfigured (e.g. MEF_EFIN missing or malformed). Nothing was sent. */
final class FilingConfigurationException extends FilingException {

    FilingConfigurationException(String message) {
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
