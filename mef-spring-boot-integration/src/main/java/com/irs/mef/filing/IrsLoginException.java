package com.irs.mef.filing;

public final class IrsLoginException extends RuntimeException {

    public IrsLoginException(String message) {
        super(message);
    }

    public IrsLoginException(String message, Throwable cause) {
        super(message, cause);
    }
}
