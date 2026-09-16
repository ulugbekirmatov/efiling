package com.irs.mef.reportingagent;

/**
 * Boundary error for Reporting Agent document construction or ATS gating.
 * Never an IRS wire fault — those stay in NewSend.
 */
public final class ReportingAgentValidationException extends RuntimeException {

    private final String field;

    public ReportingAgentValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
