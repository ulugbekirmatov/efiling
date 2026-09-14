package com.irs.mef.reportingagent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Objects;

/** Package-private. The only place header XML is written. */
final class ReportingAgentReturnXml {

    static final String NS = "http://www.irs.gov/efile";

    private static final DateTimeFormatter RETURN_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private ReportingAgentReturnXml() {}

    static String render(ReportingAgentOriginator originator, Client941Body client, OffsetDateTime returnTs) {
        OffsetDateTime truncated = returnTs.withNano(0);
        StringBuilder xml = new StringBuilder(4096);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<Return xmlns=\"").append(NS).append("\" returnVersion=\"")
                .append(escape(client.returnVersion())).append("\">\n");
        xml.append("  <ReturnHeader binaryAttachmentCnt=\"0\">\n");
        element(xml, 2, "ReturnTypeCd", "941");
        element(xml, 2, "ReturnTs", RETURN_TS.format(truncated));
        element(xml, 2, "SoftwareId", originator.softwareId().value());
        element(xml, 2, "MultSoftwarePackagesUsedInd", "false");
        filingSecurity(xml, client, truncated);
        element(xml, 2, "QuarterEndingDt", client.quarterEnding().toString());
        xml.append("    ").append(client.filerXml()).append('\n');
        xml.append("    <OriginatorGrp>\n");
        element(xml, 3, "EFIN", originator.efin().value());
        element(xml, 3, "OriginatorTypeCd", ReportingAgentReturn.ORIGINATOR_TYPE);
        xml.append("    </OriginatorGrp>\n");
        xml.append("    <ReportingAgentPINGrp>\n");
        element(xml, 3, "PIN", originator.pin().value());
        element(xml, 3, "RAPINEnteredByCd", ReportingAgentReturn.RAPIN_ENTERED_BY);
        element(xml, 3, "JuratDisclosureCd", ReportingAgentReturn.JURAT);
        xml.append("    </ReportingAgentPINGrp>\n");
        element(xml, 2, "IRSResponsiblePrtyInfoCurrInd", "true");
        element(xml, 2, "DiscussWithThirdPartyNoInd", "X");
        reportingAgentFiler(xml, originator.identity());
        xml.append("  </ReturnHeader>\n");
        xml.append("  ").append(client.returnDataXml()).append('\n');
        xml.append("</Return>\n");
        return xml.toString();
    }

    /**
     * Schema-valid security block. DeviceIdType is 40 or 64 uppercase hex, not "DEVICE001".
     * IPAddress is the complex IPv4AddressTxt wrapper, not a bare text node.
     * VendorControlNum is 16 alphanumeric, derived from filer EIN + period (not a secret).
     */
    private static void filingSecurity(StringBuilder xml, Client941Body client, OffsetDateTime returnTs) {
        Objects.requireNonNull(returnTs, "returnTs");
        String deviceId = sha1HexUpper("pyramos-ats" + client.filerEin().value());
        xml.append("    <FilingSecurityInformation>\n");
        xml.append("      <IPAddress>\n");
        element(xml, 4, "IPv4AddressTxt", "127.0.0.1");
        xml.append("      </IPAddress>\n");
        element(xml, 3, "TotActiveTimePrepSubmissionTs", "1");
        element(xml, 3, "VendorControlNum", vendorControl(client));
        element(xml, 3, "AtSubmissionCreationDeviceId", deviceId);
        element(xml, 3, "AtSubmissionFilingDeviceId", deviceId);
        xml.append("    </FilingSecurityInformation>\n");
    }

    private static void reportingAgentFiler(StringBuilder xml, ReportingAgentIdentity identity) {
        UsAddress address = identity.address();
        xml.append("    <ReportingAgent94XFilerGrp>\n");
        element(xml, 3, "EIN", identity.ein().value());
        xml.append("      <BusinessName>\n");
        element(xml, 4, "BusinessNameLine1Txt", identity.businessName());
        xml.append("      </BusinessName>\n");
        element(xml, 3, "BusinessNameControlTxt", identity.nameControl().value());
        xml.append("      <USAddress>\n");
        element(xml, 4, "AddressLine1Txt", address.line1());
        element(xml, 4, "CityNm", address.city());
        element(xml, 4, "StateAbbreviationCd", address.state());
        element(xml, 4, "ZIPCd", address.zip());
        xml.append("      </USAddress>\n");
        xml.append("    </ReportingAgent94XFilerGrp>\n");
    }

    private static String vendorControl(Client941Body client) {
        String seed = client.filerEin().value()
                + client.taxPeriod().begin()
                + client.taxPeriod().end();
        return sha256Hex(seed).substring(0, 16);
    }

    private static String sha1HexUpper(String seed) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(seed.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().withUpperCase().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 unavailable", e);
        }
    }

    private static String sha256Hex(String seed) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static void element(StringBuilder xml, int indent, String name, String value) {
        xml.append("  ".repeat(indent))
                .append('<').append(name).append('>')
                .append(escape(value))
                .append("</").append(name).append(">\n");
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
