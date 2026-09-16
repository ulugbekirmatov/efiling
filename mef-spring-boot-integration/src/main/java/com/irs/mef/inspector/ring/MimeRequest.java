package com.irs.mef.inspector.ring;

import java.util.List;
import java.util.Optional;

/**
 * Honest MIME request. Not a SOAP-only envelope pretending to be the wire.
 * soapCapture is the SOAP 1.1 part after redaction.
 * attachments are descriptors; the ZIP is gone.
 */
public record MimeRequest(
        String contentType,
        SoapCapture soapCapture,
        List<OmittedAttachment> attachments) {

    public MimeRequest {
        contentType = contentType == null ? "" : contentType;
        soapCapture = soapCapture == null ? new SoapCapture.Missing("SOAP part was not captured") : soapCapture;
        attachments = List.copyOf(attachments == null ? List.of() : attachments);
    }

    public Optional<CapturedXml> soapPart() {
        return soapCapture instanceof SoapCapture.Present present
                ? Optional.of(present.xml())
                : Optional.empty();
    }
}
