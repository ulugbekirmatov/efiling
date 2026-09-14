package com.irs.mef.inspector.ring;

import com.irs.mef.inspector.capture.SoapRedactor;
import com.irs.mef.newsend.domain.FormType;
import com.irs.mef.newsend.domain.TaxPeriod;

import java.time.Instant;
import java.util.Objects;

/**
 * One captured send. Constructed only after SoapRedactor runs.
 * Invariant: soapRequest / soapResponse documents contain no saml:Assertion,
 * wsse:UsernameToken, or wsse:BinarySecurityToken (enforced by factory, not by hope).
 * Invariant: no attachment payload bytes — OmittedAttachment is the only attachment type.
 */
public record SendSnapshot(
        SnapshotId submissionId,
        Instant capturedAt,
        String environment,
        String einMasked,
        FormType formType,
        TaxPeriod taxPeriod,
        String clientRequestId,
        String returnXmlSha256,
        CapturedXml returnXml,
        CapturedXml manifestXml,
        MimeRequest mimeRequest,
        SoapCapture soapResponse) {

    public SendSnapshot {
        Objects.requireNonNull(submissionId);
        Objects.requireNonNull(capturedAt);
        Objects.requireNonNull(formType);
        Objects.requireNonNull(taxPeriod);
        Objects.requireNonNull(returnXml);
        Objects.requireNonNull(manifestXml);
        Objects.requireNonNull(mimeRequest);
        soapResponse = soapResponse == null ? new SoapCapture.Missing("no inbound SOAP") : soapResponse;
        environment = environment == null ? "" : environment;
        einMasked = einMasked == null ? "" : einMasked;
        clientRequestId = clientRequestId == null ? "" : clientRequestId;
        if (!returnXml.sha256().equalsIgnoreCase(returnXmlSha256)) {
            throw new IllegalArgumentException("returnXml bytes must hash to the captured fingerprint");
        }
        SoapRedactor.assertNoCredentials(mimeRequest.soapPart().orElse(null));
        if (soapResponse instanceof SoapCapture.Present present) {
            SoapRedactor.assertNoCredentials(present.xml());
        }
    }

    public SnapshotSummary summary() {
        return new SnapshotSummary(
                submissionId,
                capturedAt,
                einMasked,
                formType.code(),
                taxPeriod.begin() + " to " + taxPeriod.end(),
                mimeRequest.soapPart().isPresent());
    }
}
