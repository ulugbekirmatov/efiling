package com.irs.mef.reportingagent;

import com.irs.mef.newsend.domain.Efin;
import com.irs.mef.newsend.domain.Ein;

/**
 * Schema-valid literals for offline tests. These are XSD stand-ins, not Pyramos enrollment values,
 * and must never be used on the ATS path.
 */
final class ReportingAgentOriginatorStandIn {

    private ReportingAgentOriginatorStandIn() {}

    static ReportingAgentOriginator pyramosShaped() {
        // EFIN 000000 is legal EFINType; NOT 238689 (OneWell). Not IRS-issued.
        // PIN 00000 is legal PINType; NOT 1234567890 (OnlineFiler SignatureType).
        // SoftwareId 00000000 is legal; NOT 12345678.
        // Agent EIN 000000000 is legal EINType; NOT 003000004 (Orchid filer).
        return new ReportingAgentOriginator(
                new Efin("000000"),
                new ReportingAgentPin("00000"),
                new SoftwareId("00000000"),
                new ReportingAgentIdentity(
                        new Ein("000000000"),
                        "PYRAMOS SOFTWARE LLC",
                        new NameControl("PYRA"),
                        new UsAddress("100 Market Street", "Philadelphia", "PA", "19103")));
    }
}
