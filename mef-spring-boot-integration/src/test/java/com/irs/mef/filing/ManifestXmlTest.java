package com.irs.mef.filing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Offline. Pins the two manifest invariants against efileAttachments.xsd:
 * normative element order, and EFIN derived from the submission id.
 */
class ManifestXmlTest {

    private static Filing filing(FormSpec formType) {
        Originator originator = ReportingAgentOriginatorStandIn.pyramosShaped();
        SubmitCommand command = new SubmitCommand(
                "key-1", "orchid", new Ein("003000004"), formType,
                new TaxPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31)),
                "<Return xmlns=\"" + Efile.NAMESPACE + "\"/>");
        return new Filing(new SubmissionId(originator.efin().value() + "2026090abc1234"), command,
                Instant.parse("2026-03-31T12:00:00Z"));
    }

    @Test
    @DisplayName("elements appear in the schema's normative order")
    void elementOrderMatchesSchema() {
        String xml = ManifestXml.render(filing(FormSpec.F941));

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
        String xml = ManifestXml.render(filing(FormSpec.F941));

        assertTrue(xml.contains("<SubmissionId>0000002026090abc1234</SubmissionId>"));
        assertTrue(xml.contains("<EFIN>000000</EFIN>"));
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
        assertTrue(ManifestXml.render(filing(FormSpec.F940))
                .contains("<FederalSubmissionTypeCd>940</FederalSubmissionTypeCd>"));
        assertTrue(ManifestXml.render(filing(FormSpec.F941X))
                .contains("<FederalSubmissionTypeCd>941X</FederalSubmissionTypeCd>"));
    }

    @Test
    @DisplayName("id prefix, manifest EFIN, and OriginatorGrp EFIN are one originator's EFIN")
    void threeEfinPlacesComeFromOneOriginator() {
        Originator originator = ReportingAgentOriginatorStandIn.pyramosShaped();
        Clock clock = Clock.fixed(Instant.parse("2026-10-13T14:00:00Z"), java.time.ZoneOffset.UTC);
        ReturnComposer composed = ReturnComposer.compose(
                originator, ClientReturnBody.load(AtsScenario.ORCHID_941.clientBody()), clock);
        SubmissionId id = SubmissionId.mint(originator, clock.instant(), Efile.PROCESSING_ZONE,
                new java.util.Random(1));
        String manifest = ManifestXml.render(new Filing(
                id, composed.toSubmitCommand("key", "orchid"), clock.instant()));
        String efin = originator.efin().value();

        assertEquals(efin, id.value().substring(0, 6));
        assertEquals(efin, id.efin().value());
        assertTrue(manifest.contains("<EFIN>" + efin + "</EFIN>"), manifest);
        assertEquals(efin, composed.structure().originatorEfin().value());
    }

    @Test
    @DisplayName("no element is ever emitted empty")
    void noEmptyElements() {
        String xml = ManifestXml.render(filing(FormSpec.F941));

        assertEquals(-1, xml.indexOf("></"), "empty element found in:\n" + xml);
    }

    @Test
    @DisplayName("manifest root binds xmlns:efile to the efile namespace")
    void rootDeclaresEfilePrefix() {
        String xml = ManifestXml.render(filing(FormSpec.F941));
        String root = xml.lines()
                .filter(line -> line.startsWith("<IRSSubmissionManifest"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no IRSSubmissionManifest root in:\n" + xml));
        assertEquals(
                "<IRSSubmissionManifest xmlns=\"http://www.irs.gov/efile\" xmlns:efile=\"http://www.irs.gov/efile\">",
                root);
    }
}
