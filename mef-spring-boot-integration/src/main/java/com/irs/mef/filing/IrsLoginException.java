package com.irs.mef.filing;

/** LoginClient failed. Nothing was sent. */
public final class IrsLoginException extends RuntimeException {

    public IrsLoginException(String message) {
        super(message);
    }

    public IrsLoginException(String message, Throwable cause) {
        super(message, cause);
    }
}
