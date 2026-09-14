package com.irs.mef.reportingagent;

import com.irs.mef.config.MefSdkConfig;

/**
 * Session/document alignment for the ATS test. Not a Spring bean used by HTTP.
 * 10219201 lives here as the expected AppSysID, never as a Return field.
 */
public final class ReportingAgentAtsGate {

    public static final String EXPECTED_ASID = "10219201";

    private ReportingAgentAtsGate() {}

    public static void open(MefSdkConfig sdk, ReportingAgentOriginator originator) {
        if (!"ATS".equalsIgnoreCase(sdk.getEnvironment())) {
            throw new ReportingAgentValidationException("environment", "RA ATS test requires mef.sdk.environment=ATS");
        }
        String asid = sdk.getAuthentication() != null ? sdk.getAuthentication().getAsid() : null;
        if (!EXPECTED_ASID.equals(asid)) {
            throw new ReportingAgentValidationException("asid",
                    "MEF_ASID must be " + EXPECTED_ASID + " (A2A AppSysID). Refusing to file under '" + asid + "'");
        }
        String etin = sdk.getAuthentication().getEtin();
        if (etin == null || etin.isBlank()) {
            throw new ReportingAgentValidationException("etin", "MEF_ETIN is required for login");
        }
        if (!sdk.isCertificateConfigured()) {
            throw new ReportingAgentValidationException("certificate", "MEF_KEYSTORE_* must be set");
        }
        String configuredEfin = sdk.getAuthentication().getEfin();
        if (!originator.efin().value().equals(configuredEfin)) {
            throw new ReportingAgentValidationException("efin",
                    "Originator EFIN " + originator.efin().value()
                            + " must equal MEF_EFIN " + configuredEfin
                            + " (id prefix, OriginatorGrp, and manifest share this value)");
        }
        // Deliberately NOT: asid.startsWith(efin). AE "EFIN+2" is unconfirmed; do not encode it.
    }
}
