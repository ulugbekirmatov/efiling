package com.irs.mef.filing;

import org.springframework.http.HttpStatus;

/**
 * Root of the NewSend error hierarchy. Each subclass carries its own HTTP status.
 *
 * {@link #resubmitSafe()} answers the only question a filing caller actually has:
 * may this request be sent again without risking a duplicate federal filing?
 */
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
