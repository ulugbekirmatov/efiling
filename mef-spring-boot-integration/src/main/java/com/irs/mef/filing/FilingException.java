package com.irs.mef.filing;

import org.springframework.http.HttpStatus;

public abstract sealed class FilingException extends RuntimeException
        permits FilingValidationException, FilingDuplicateException,
                FilingBusyException, FilingConfigurationException {

    FilingException(String message) {
        super(message);
    }

    FilingException(String message, Throwable cause) {
        super(message, cause);
    }

    public abstract HttpStatus status();

    public abstract String errorCode();

    public abstract boolean resubmitSafe();
}
