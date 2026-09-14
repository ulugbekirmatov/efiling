package com.irs.mef.reportingagent;

final class ReportingAgentChecks {

    private ReportingAgentChecks() {}

    static void requireText(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new ReportingAgentValidationException(field, field + " is required");
        }
    }

    static String requireConfigured(String envName, String value) {
        if (value == null || value.isBlank()) {
            throw new ReportingAgentValidationException(envName, envName + " is required");
        }
        return value;
    }
}
