package com.irs.mef.filing;

import org.springframework.core.env.Environment;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public record Originator(
        Efin efin,
        ReportingAgentPin pin,
        SoftwareIds softwareIds,
        String filingIpv4Address,
        ReportingAgentIdentity identity) {

    /** R0000-118-01: an EFIN with these first two digits must file as OnlineFiler. */
    static final Set<String> ONLINE_FILER_EFIN_PREFIXES = Set.of("10", "21", "32", "44", "53");

    private static final String ONEWELL_EFIN = "238689"; // repo-guard:allow onewell-id reason="refuses a stale .env that still has the OneWell EFIN" expires=2027-08-21 approved-by=ulugbekirmatov
    private static final String STAND_IN_EFIN = "000000";
    private static final String ORCHID_EIN = "003000004";

    private static final Pattern DOTTED_IPV4 =
            Pattern.compile("^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$");
    private static final String LOOPBACK_PREFIX = "127.";
    private static final String UNSPECIFIED_ADDRESS = "0.0.0.0";

    public Originator {
        Objects.requireNonNull(efin);
        Objects.requireNonNull(pin);
        Objects.requireNonNull(softwareIds);
        Objects.requireNonNull(identity);
        requireReportingAgentEfin(efin);
        requireRoutableIpv4(filingIpv4Address);
    }

    public static Originator fromEnvironment(Environment env) {
        String efinValue = ReportingAgentChecks.requireConfigured("MEF_EFIN", env.getProperty("MEF_EFIN"));
        if (ONEWELL_EFIN.equals(efinValue)) {
            throw new ReportingAgentValidationException("efin",
                    "MEF_EFIN " + ONEWELL_EFIN + " is the OneWell originator; refuse");
        }
        if (STAND_IN_EFIN.equals(efinValue)) {
            throw new ReportingAgentValidationException("efin",
                    "MEF_EFIN 000000 is the schema stand-in; refuse");
        }
        String einRaw = ReportingAgentChecks.requireConfigured("MEF_RA_EIN", env.getProperty("MEF_RA_EIN"));
        if (ORCHID_EIN.equals(einRaw)) {
            throw new ReportingAgentValidationException("ein",
                    "MEF_RA_EIN 003000004 is the Orchid client EIN; refuse");
        }
        return new Originator(
                new Efin(efinValue),
                new ReportingAgentPin(ReportingAgentChecks.requireConfigured(
                        "MEF_RA_PIN", env.getProperty("MEF_RA_PIN"))),
                softwareIds(env),
                ReportingAgentChecks.requireConfigured(
                        "MEF_RA_FILING_IP", env.getProperty("MEF_RA_FILING_IP")),
                new ReportingAgentIdentity(
                        new Ein(einRaw),
                        ReportingAgentChecks.requireConfigured(
                                "MEF_RA_NAME", env.getProperty("MEF_RA_NAME")),
                        new NameControl(ReportingAgentChecks.requireConfigured(
                                "MEF_RA_NAME_CONTROL", env.getProperty("MEF_RA_NAME_CONTROL"))),
                        new UsAddress(
                                ReportingAgentChecks.requireConfigured(
                                        "MEF_RA_ADDRESS_LINE1", env.getProperty("MEF_RA_ADDRESS_LINE1")),
                                ReportingAgentChecks.requireConfigured(
                                        "MEF_RA_CITY", env.getProperty("MEF_RA_CITY")),
                                ReportingAgentChecks.requireConfigured(
                                        "MEF_RA_STATE", env.getProperty("MEF_RA_STATE")),
                                ReportingAgentChecks.requireConfigured(
                                        "MEF_RA_ZIP", env.getProperty("MEF_RA_ZIP")))));
    }

    @Override
    public String toString() {
        return "Originator[efin=" + efin.value()
                + ", pin=*****"
                + ", softwareIds=" + softwareIds
                + ", filingIpv4Address=" + filingIpv4Address
                + ", identity=" + identity
                + "]";
    }

    private static SoftwareIds softwareIds(Environment env) {
        Map<SchemaRelease, SoftwareId> ids = new EnumMap<>(SchemaRelease.class);
        for (SchemaRelease release : SchemaRelease.values()) {
            String raw = env.getProperty(release.softwareIdEnvKey());
            if (raw != null && !raw.isBlank()) {
                ids.put(release, new SoftwareId(raw.trim()));
            }
        }
        String quarterly = SchemaRelease.Y2026Q1_V4.softwareIdEnvKey();
        if (!ids.containsKey(SchemaRelease.Y2026Q1_V4)) {
            throw new ReportingAgentValidationException(quarterly, quarterly + " is required");
        }
        return new SoftwareIds(ids);
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
