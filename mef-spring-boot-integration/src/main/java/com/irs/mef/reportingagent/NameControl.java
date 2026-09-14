package com.irs.mef.reportingagent;

import java.util.regex.Pattern;

/** IRS BusinessNameControlType: 1–4 of A-Z, 0-9, hyphen, ampersand. No spaces. */
public record NameControl(String value) {
    private static final Pattern PATTERN = Pattern.compile("^[A-Z0-9\\-&]{1,4}$");

    public NameControl {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new ReportingAgentValidationException("nameControl",
                    "Name control must be 1-4 characters from A-Z, 0-9, hyphen, ampersand");
        }
    }
}
