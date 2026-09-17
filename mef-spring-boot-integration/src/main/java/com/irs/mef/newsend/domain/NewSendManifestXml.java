package com.irs.mef.newsend.domain;

/**
 * IRS submission manifest. Pure function, no SDK, no Spring.
 *
 * Element ORDER is normative (efileAttachments.xsd sequence):
 * SubmissionId, EFIN, TaxYr, GovernmentCd, FederalSubmissionTypeCd,
 * [TaxPeriodBeginDt], [TaxPeriodEndDt], TIN.
 * TaxYr is minOccurs=0 in the XSD. R0000-143 Reject and Stop requires a value.
 *
 * No caller-controlled free text reaches this XML — every input is a validated digits-only
 * value type or a closed enum — so there is no injection surface by construction.
 * Nothing is ever emitted as an empty element (an empty EFIN/TIN is schema-invalid).
 */
public final class NewSendManifestXml {

    private static final String NAMESPACE = "http://www.irs.gov/efile";
    private static final String GOVERNMENT_CD = "IRS";

    private NewSendManifestXml() {
    }

    public static String render(NewSendFiling filing) {
        NewSendSubmitCommand command = filing.command();
        StringBuilder xml = new StringBuilder(512);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<IRSSubmissionManifest xmlns=\"").append(NAMESPACE)
                .append("\" xmlns:efile=\"").append(NAMESPACE).append("\">\n");
        element(xml, "SubmissionId", filing.submissionId().value());
        element(xml, "EFIN", filing.efin().value());
        element(xml, "TaxYr", Integer.toString(command.taxPeriod().end().getYear()));
        element(xml, "GovernmentCd", GOVERNMENT_CD);
        element(xml, "FederalSubmissionTypeCd", command.formType().code());
        element(xml, "TaxPeriodBeginDt", command.taxPeriod().begin().toString());
        element(xml, "TaxPeriodEndDt", command.taxPeriod().end().toString());
        element(xml, "TIN", command.clientEin().value());
        xml.append("</IRSSubmissionManifest>\n");
        return xml.toString();
    }

    private static void element(StringBuilder xml, String name, String value) {
        xml.append("  <").append(name).append('>').append(value).append("</").append(name).append(">\n");
    }
}
