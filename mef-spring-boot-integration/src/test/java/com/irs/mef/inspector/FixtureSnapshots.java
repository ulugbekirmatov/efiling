package com.irs.mef.inspector;

import com.irs.mef.inspector.ring.CapturedXml;
import com.irs.mef.inspector.ring.MimeRequest;
import com.irs.mef.inspector.ring.OmittedAttachment;
import com.irs.mef.inspector.ring.SendSnapshot;
import com.irs.mef.inspector.ring.SnapshotId;
import com.irs.mef.inspector.ring.SoapCapture;
import com.irs.mef.newsend.domain.Ein;
import com.irs.mef.newsend.domain.FormType;
import com.irs.mef.newsend.domain.NewSendFiling;
import com.irs.mef.newsend.domain.NewSendManifestXml;
import com.irs.mef.newsend.domain.NewSendSubmissionId;
import com.irs.mef.newsend.domain.NewSendSubmitCommand;
import com.irs.mef.newsend.domain.TaxPeriod;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Shared Orchid Q1 2026 snapshot used by panel and capture tests. */
public final class FixtureSnapshots {

    public static final String SUBMISSION_ID = "2386892026090abc1234";
    public static final String RETURN_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Return xmlns="http://www.irs.gov/efile" returnVersion="2026Q1v4.0">
              <ReturnHeader>
                <ReturnTypeCd>941</ReturnTypeCd>
                <Filer><EIN>003000004</EIN></Filer>
              </ReturnHeader>
            </Return>
            """;

    private FixtureSnapshots() {
    }

    public static NewSendFiling orchidFiling() {
        NewSendSubmitCommand command = new NewSendSubmitCommand(
                "inspector-demo-1",
                "orchid",
                new Ein("003000004"),
                FormType.F941,
                new TaxPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31)),
                RETURN_XML,
                false);
        return new NewSendFiling(
                new NewSendSubmissionId(SUBMISSION_ID),
                command,
                Instant.parse("2026-03-31T12:00:00Z"));
    }

    public static SendSnapshot orchidQ1() {
        NewSendFiling filing = orchidFiling();
        CapturedXml returnXml = new CapturedXml(filing.command().returnXml());
        String redactedSoap = """
                <?xml version="1.0" encoding="UTF-8"?>
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                  <soapenv:Header>
                    <wsse:Security xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
                      <!-- credential omitted -->
                    </wsse:Security>
                  </soapenv:Header>
                  <soapenv:Body><SendSubmissionsRequest/></soapenv:Body>
                </soapenv:Envelope>
                """;
        MimeRequest mime = new MimeRequest(
                "multipart/related; type=\"text/xml\"; boundary=\"----=_Part_inspector_0\"",
                new SoapCapture.Present(new CapturedXml(redactedSoap)),
                List.of(new OmittedAttachment(
                        "<SubmissionsAttBin>",
                        "application/octet-stream",
                        377,
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")));
        return new SendSnapshot(
                new SnapshotId(SUBMISSION_ID),
                Instant.parse("2026-03-31T12:00:00Z"),
                "ATS",
                filing.command().clientEin().masked(),
                filing.command().formType(),
                filing.command().taxPeriod(),
                filing.command().clientRequestId(),
                returnXml.sha256(),
                returnXml,
                new CapturedXml(NewSendManifestXml.render(filing)),
                mime,
                new SoapCapture.Missing("no inbound SOAP"));
    }
}
