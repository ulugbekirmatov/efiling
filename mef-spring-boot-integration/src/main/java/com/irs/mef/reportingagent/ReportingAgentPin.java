package com.irs.mef.reportingagent;

import java.util.regex.Pattern;

/** IRS PINType. Exactly 5 digits. Not SignatureType (10 digits). */
public record ReportingAgentPin(String value) {
    private static final Pattern PATTERN = Pattern.compile("^[0-9]{5}$");

    public ReportingAgentPin {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new ReportingAgentValidationException("pin", "Reporting Agent PIN must be exactly 5 digits");
        }
    }
}
