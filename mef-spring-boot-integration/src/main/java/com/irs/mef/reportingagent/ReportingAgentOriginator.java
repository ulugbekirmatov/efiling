package com.irs.mef.reportingagent;

import com.irs.mef.newsend.domain.Efin;

import java.util.Objects;

/**
 * Pyramos as originator. OriginatorTypeCd is not a field — compose always writes ReportingAgent.
 * ETIN and ASID are not fields — they belong to the A2A session, not the Return.
 */
public record ReportingAgentOriginator(
        Efin efin,
        ReportingAgentPin pin,
        SoftwareId softwareId,
        ReportingAgentIdentity identity) {
    public ReportingAgentOriginator {
        Objects.requireNonNull(efin);
        Objects.requireNonNull(pin);
        Objects.requireNonNull(softwareId);
        Objects.requireNonNull(identity);
        if (efin.value().equals(identity.ein().value())) {
            // EFIN is 6 digits, EIN is 9 — this cannot be equal. Left as a comment invariant.
        }
    }
}
