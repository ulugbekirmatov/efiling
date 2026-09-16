package com.irs.mef.reportingagent;

import com.irs.mef.newsend.domain.Ein;

import java.util.Objects;

import static com.irs.mef.reportingagent.ReportingAgentChecks.requireText;

/**
 * Pyramos as ReportingAgent94XFilerGrp. Required by this type even though the XSD
 * lists the group as minOccurs=0 — EMPL-003 is treated as load-bearing for RA originators.
 * Distinct from Client941Body.filerEin.
 */
public record ReportingAgentIdentity(
        Ein ein,
        String businessName,
        NameControl nameControl,
        UsAddress address) {
    public ReportingAgentIdentity {
        Objects.requireNonNull(ein);
        requireText("businessName", businessName);
        Objects.requireNonNull(nameControl);
        Objects.requireNonNull(address);
    }
}
