package com.irs.mef.inspector.ring;

import com.irs.mef.inspector.FixtureSnapshots;
import com.irs.mef.newsend.domain.NewSendSubmitCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CapturedXmlTest {

    @Test
    @DisplayName("sha256 is of packed original UTF-8, never pretty()")
    void hashIsOfOriginalNotPretty() {
        CapturedXml xml = new CapturedXml(FixtureSnapshots.RETURN_XML);
        String expected = new NewSendSubmitCommand(
                "inspector-demo-1", "orchid",
                new com.irs.mef.newsend.domain.Ein("003000004"),
                com.irs.mef.newsend.domain.FormType.F941,
                new com.irs.mef.newsend.domain.TaxPeriod(
                        java.time.LocalDate.of(2026, 1, 1), java.time.LocalDate.of(2026, 3, 31)),
                FixtureSnapshots.RETURN_XML, false).returnXmlSha256();
        assertEquals(expected, xml.sha256());
        assertEquals(com.irs.mef.inspector.Sha256Hex.ofUtf8(xml.original()), xml.sha256(),
                "sha256() must hash original(), never pretty()");
    }

    @Test
    @DisplayName("SendSnapshot ctor refuses SOAP that still contains credentials")
    void snapshotRejectsSaml() {
        SendSnapshot good = FixtureSnapshots.orchidQ1();
        MimeRequest dirty = new MimeRequest(
                good.mimeRequest().contentType(),
                new SoapCapture.Present(new CapturedXml(
                        "<Envelope><saml:Assertion xmlns:saml=\"urn:oasis:names:tc:SAML:1.0:assertion\">x</saml:Assertion></Envelope>")),
                good.mimeRequest().attachments());
        assertThrows(IllegalArgumentException.class, () -> new SendSnapshot(
                good.submissionId(), good.capturedAt(), good.environment(), good.einMasked(),
                good.formType(), good.taxPeriod(), good.clientRequestId(), good.returnXmlSha256(),
                good.returnXml(), good.manifestXml(), dirty, good.soapResponse()));
    }
}
