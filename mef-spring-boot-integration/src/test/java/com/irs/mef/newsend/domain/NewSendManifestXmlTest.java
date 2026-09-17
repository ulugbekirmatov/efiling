package com.irs.mef.newsend.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Offline. Pins the two manifest invariants against efileAttachments.xsd:
 * normative element order, and EFIN derived from the submission id.
 */
class NewSendManifestXmlTest {

    private static NewSendFiling filing(FormType formType) {
        NewSendSubmitCommand command = new NewSendSubmitCommand(
                "key-1", "orchid", new Ein("003000004"), formType,
                new TaxPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31)),
                "<Return xmlns=\"http://www.irs.gov/efile\"/>", false);
        return new NewSendFiling(new NewSendSubmissionId("2386892026090abc1234"), command,
                Instant.parse("2026-03-31T12:00:00Z"));
    }

    @Test
    @DisplayName("elements appear in the schema's normative order")
    void elementOrderMatchesSchema() {
        String xml = NewSendManifestXml.render(filing(FormType.F941));

        String[] orderedElements = {"<SubmissionId>", "<EFIN>", "<TaxYr>", "<GovernmentCd>",
                "<FederalSubmissionTypeCd>", "<TaxPeriodBeginDt>", "<TaxPeriodEndDt>", "<TIN>"};
        int previousIndex = -1;
        for (String element : orderedElements) {
            int index = xml.indexOf(element);
            assertTrue(index > previousIndex, element + " out of order or missing in:\n" + xml);
            previousIndex = index;
        }
    }

    @Test
    @DisplayName("EFIN comes from the submission id; TIN and dates from the command")
    void valuesComeFromTheRightSources() {
        String xml = NewSendManifestXml.render(filing(FormType.F941));

        assertTrue(xml.contains("<SubmissionId>2386892026090abc1234</SubmissionId>"));
        assertTrue(xml.contains("<EFIN>238689</EFIN>"));
        assertTrue(xml.contains("<TaxYr>2026</TaxYr>"));
        assertTrue(xml.contains("<GovernmentCd>IRS</GovernmentCd>"));
        assertTrue(xml.contains("<FederalSubmissionTypeCd>941</FederalSubmissionTypeCd>"));
        assertTrue(xml.contains("<TaxPeriodBeginDt>2026-01-01</TaxPeriodBeginDt>"));
        assertTrue(xml.contains("<TaxPeriodEndDt>2026-03-31</TaxPeriodEndDt>"));
        assertTrue(xml.contains("<TIN>003000004</TIN>"));
        assertTrue(xml.contains("xmlns=\"http://www.irs.gov/efile\""));
    }

    @Test
    @DisplayName("the form type is never hardcoded — each 94x code lands in the manifest")
    void formTypeIsExplicit() {
        assertTrue(NewSendManifestXml.render(filing(FormType.F940))
                .contains("<FederalSubmissionTypeCd>940</FederalSubmissionTypeCd>"));
        assertTrue(NewSendManifestXml.render(filing(FormType.F941X))
                .contains("<FederalSubmissionTypeCd>941X</FederalSubmissionTypeCd>"));
        assertTrue(NewSendManifestXml.render(filing(FormType.F944))
                .contains("<FederalSubmissionTypeCd>944</FederalSubmissionTypeCd>"));
    }

    @Test
    @DisplayName("no element is ever emitted empty")
    void noEmptyElements() {
        String xml = NewSendManifestXml.render(filing(FormType.F941));

        assertEquals(-1, xml.indexOf("></"), "empty element found in:\n" + xml);
    }

    @Test
    @DisplayName("manifest root binds xmlns:efile to the efile namespace")
    void rootDeclaresEfilePrefix() {
        String xml = NewSendManifestXml.render(filing(FormType.F941));
        String root = xml.lines()
                .filter(line -> line.startsWith("<IRSSubmissionManifest"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no IRSSubmissionManifest root in:\n" + xml));
        assertEquals(
                "<IRSSubmissionManifest xmlns=\"http://www.irs.gov/efile\" xmlns:efile=\"http://www.irs.gov/efile\">",
                root);
    }
}
