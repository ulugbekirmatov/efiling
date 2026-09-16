package com.irs.mef.reportingagent;

import static com.irs.mef.reportingagent.ReportingAgentChecks.requireText;

/** USAddressType subset used for this filing. No foreign address branch. */
public record UsAddress(String line1, String city, String state, String zip) {
    public UsAddress {
        requireText("addressLine1", line1);
        requireText("city", city);
        if (state == null || !state.matches("^[A-Z]{2}$")) {
            throw new ReportingAgentValidationException("state", "state must be a 2-letter US abbreviation");
        }
        if (zip == null || !zip.matches("^[0-9]{5}([0-9]{4})?$")) {
            throw new ReportingAgentValidationException("zip", "zip must be 5 or 9 digits");
        }
    }
}
