package com.irs.mef.filing;

/**
 * Boundary error for Reporting Agent document construction or ATS gating.
 * Never an IRS wire fault — those stay in NewSend.
 */
final class ReportingAgentValidationException extends RuntimeException {

    private final String field;

    ReportingAgentValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    String field() {
        return field;
    }
}
