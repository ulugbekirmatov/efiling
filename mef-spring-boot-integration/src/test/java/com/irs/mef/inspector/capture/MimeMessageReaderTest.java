package com.irs.mef.inspector.capture;

import com.irs.mef.inspector.ring.MimeRequest;
import com.irs.mef.inspector.ring.OmittedAttachment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * First capture test: checked-in {@code .mime} fixture through the splitter.
 * Offline. No ATS.
 */
class MimeMessageReaderTest {

    @Test
    @DisplayName("fixture MIME splits to redacted SOAP plus omitted ZIP descriptor")
    void fixtureMimeThroughSplitter() throws Exception {
        byte[] rfc822;
        try (InputStream in = getClass().getResourceAsStream("/inspector/send-submissions.mime")) {
            rfc822 = in.readAllBytes();
        }

        MimeRequest request = MimeMessageReader.readMimeFile(rfc822);

        assertTrue(request.contentType().contains("multipart/related"));
        assertTrue(request.soapPart().isPresent(), "SOAP part should be captured");
        String soap = request.soapPart().get().original();
        assertFalse(soap.contains("saml:Assertion"));
        assertFalse(soap.contains("samlAssertion"));
        assertFalse(soap.contains("UsernameToken"));
        assertFalse(soap.contains("BinarySecurityToken"));
        assertFalse(soap.contains("secret-password"));
        assertTrue(soap.contains("credential omitted"));
        assertTrue(soap.contains("Timestamp") || soap.contains("Created"));
        assertTrue(soap.contains("MeFHeader") || soap.contains("MessageID") || soap.contains("msg-orchid-1"));
        assertTrue(soap.contains("SendSubmissionsRequest"));

        assertEquals(1, request.attachments().size());
        OmittedAttachment zip = request.attachments().get(0);
        assertTrue(zip.contentType().contains("octet-stream"));
        assertTrue(zip.byteLength() > 0);
        assertEquals(64, zip.sha256().length());
        assertTrue(zip.contentId().contains("SubmissionsAttBin"));

        for (Field field : OmittedAttachment.class.getDeclaredFields()) {
            assertFalse(field.getType().equals(byte[].class), "OmittedAttachment must not hold payload bytes");
        }
    }

    @Test
    @DisplayName("oversize SOAP part is Missing and does not throw")
    void oversizeSoapIsMissing() {
        String huge = "<Envelope>" + "x".repeat(2000) + "</Envelope>";
        byte[] body = huge.getBytes();
        MimeRequest request = MimeMessageReader.readRawMime(body, "text/xml", 100);
        assertTrue(request.soapPart().isEmpty());
        assertTrue(request.soapCapture() instanceof com.irs.mef.inspector.ring.SoapCapture.Missing);
    }
}
