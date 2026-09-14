package com.irs.mef.newsend.error;

import org.springframework.http.HttpStatus;

/**
 * Root of the NewSend error hierarchy. Deliberately does NOT extend MefException:
 * the legacy GlobalExceptionHandler maps every MefException to HTTP 500, and these
 * exceptions carry their own status instead.
 *
 * {@link #resubmitSafe()} answers the only question a filing caller actually has:
 * may this request be sent again without risking a duplicate federal filing?
 */
public abstract sealed class NewSendException extends RuntimeException
        permits NewSendValidationException, NewSendNotLoggedInException, NewSendDuplicateException,
                NewSendBusyException, NewSendConfigurationException {

    protected NewSendException(String message) {
        super(message);
    }

    protected NewSendException(String message, Throwable cause) {
        super(message, cause);
    }

    public abstract HttpStatus status();

    public abstract String errorCode();

    public abstract boolean resubmitSafe();
}
