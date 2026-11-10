package com.irs.mef.filing;


import java.util.Objects;

import static com.irs.mef.filing.ReportingAgentChecks.requireText;

/**
 * Pyramos as ReportingAgent94XFilerGrp. Required by this type even though the XSD
 * lists the group as minOccurs=0 — EMPL-003 is treated as load-bearing for RA originators.
 * Distinct from ClientReturnBody.filerEin.
 */
record ReportingAgentIdentity(
        Ein ein,
        String businessName,
        NameControl nameControl,
        UsAddress address) {
    ReportingAgentIdentity {
        Objects.requireNonNull(ein);
        requireText("businessName", businessName);
        Objects.requireNonNull(nameControl);
        Objects.requireNonNull(address);
    }
}
