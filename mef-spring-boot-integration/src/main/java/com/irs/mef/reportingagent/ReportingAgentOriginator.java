package com.irs.mef.reportingagent;

import com.irs.mef.newsend.domain.Efin;

import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Pyramos as originator. OriginatorTypeCd is not a field — compose always writes ReportingAgent.
 * ETIN and ASID are not fields — they belong to the A2A session, not the Return.
 *
 * <p>Refuses the IRS rejects ATS already returned for this header, so they fail before a send:
 * R0000-118-01 (2026-09-18, EFIN 102192) and R0000-244 (2026-09-25, loopback filing IP).
 */
public record ReportingAgentOriginator(
        Efin efin,
        ReportingAgentPin pin,
        SoftwareId softwareId,
        String filingIpv4Address,
        ReportingAgentIdentity identity) {

    /** R0000-118-01: an EFIN with these first two digits must file as OnlineFiler. */
    static final Set<String> ONLINE_FILER_EFIN_PREFIXES = Set.of("10", "21", "32", "44", "53");

    private static final Pattern DOTTED_IPV4 =
            Pattern.compile("^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$");
    private static final String LOOPBACK_PREFIX = "127.";
    private static final String UNSPECIFIED_ADDRESS = "0.0.0.0";

    public ReportingAgentOriginator {
        Objects.requireNonNull(efin);
        Objects.requireNonNull(pin);
        Objects.requireNonNull(softwareId);
        Objects.requireNonNull(identity);
        requireReportingAgentEfin(efin);
        requireRoutableIpv4(filingIpv4Address);
    }

    private static void requireReportingAgentEfin(Efin efin) {
        String prefix = efin.value().substring(0, 2);
        if (ONLINE_FILER_EFIN_PREFIXES.contains(prefix)) {
            throw new ReportingAgentValidationException("efin",
                    "EFIN " + efin.value() + " starts with " + prefix + ": IRS rule R0000-118-01 rejects a "
                            + "ReportingAgent return under an online-filer EFIN (prefixes 10/21/32/44/53). "
                            + "Set MEF_EFIN to the non-online EFIN.");
        }
    }

    private static void requireRoutableIpv4(String address) {
        ReportingAgentChecks.requireText("filingIpv4Address", address);
        if (!DOTTED_IPV4.matcher(address).matches()) {
            throw new ReportingAgentValidationException("filingIpv4Address",
                    "MEF_RA_FILING_IP must be a dotted IPv4 address, got '" + address + "'");
        }
        if (address.startsWith(LOOPBACK_PREFIX) || address.equals(UNSPECIFIED_ADDRESS)) {
            throw new ReportingAgentValidationException("filingIpv4Address",
                    "MEF_RA_FILING_IP " + address + " is not routable: IRS rule R0000-244 rejects it. "
                            + "Use the public IPv4 of the transmitting host.");
        }
    }
}
