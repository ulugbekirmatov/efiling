package com.irs.mef.filing;

/** LoginClient failed. Nothing was sent. */
final class IrsLoginException extends RuntimeException {

    IrsLoginException(String message) {
        super(message);
    }

    IrsLoginException(String message, Throwable cause) {
        super(message, cause);
    }
}
