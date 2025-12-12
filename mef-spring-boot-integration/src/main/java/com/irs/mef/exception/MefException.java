package com.irs.mef.exception;

/**
 * Custom exception class for MeF Client SDK operations.
 * Wraps all exceptions that occur during IRS MeF A2A service interactions.
 */
public class MefException extends RuntimeException {

    private final String errorCode;
    private final String detail;

    public MefException(String message) {
        super(message);
        this.errorCode = "MEF_ERROR";
        this.detail = null;
    }

    public MefException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "MEF_ERROR";
        this.detail = null;
    }

    public MefException(String errorCode, String message, String detail) {
        super(message);
        this.errorCode = errorCode;
        this.detail = detail;
    }

    public MefException(String errorCode, String message, String detail, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.detail = detail;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getDetail() {
        return detail;
    }
}
