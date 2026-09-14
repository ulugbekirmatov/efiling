package com.irs.mef.reportingagent;

import java.util.regex.Pattern;

/** IRS SoftwareIdType. Exactly 8 digits. */
public record SoftwareId(String value) {
    private static final Pattern PATTERN = Pattern.compile("^[0-9]{8}$");

    public SoftwareId {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new ReportingAgentValidationException("softwareId", "Software ID must be exactly 8 digits");
        }
    }
}
