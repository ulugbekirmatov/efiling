package com.irs.mef.inspector.capture;

import com.irs.mef.inspector.ring.CapturedXml;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoapRedactorTest {

    private static final String SOAP = """
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" \
            xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd" \
            xmlns:saml="urn:oasis:names:tc:SAML:1.0:assertion">
              <soapenv:Header>
                <wsse:Security>
                  <saml:Assertion>token</saml:Assertion>
                  <wsse:UsernameToken><wsse:Password>secret</wsse:Password></wsse:UsernameToken>
                  <wsse:BinarySecurityToken>cert</wsse:BinarySecurityToken>
                </wsse:Security>
              </soapenv:Header>
              <soapenv:Body><SendSubmissionsRequest/></soapenv:Body>
            </soapenv:Envelope>
            """;

    @Test
    @DisplayName("Assertion, UsernameToken, and BinarySecurityToken become credential comments")
    void dropsCredentialsAndKeepsBody() {
        String redacted = SoapRedactor.redact(SOAP);
        assertFalse(SoapRedactor.containsCredential(redacted));
        assertTrue(redacted.contains("credential omitted"));
        assertTrue(redacted.contains("SendSubmissionsRequest"));
        SoapRedactor.assertNoCredentials(new CapturedXml(redacted));
    }

    @Test
    @DisplayName("assertNoCredentials refuses leftover saml:Assertion")
    void assertRejectsResidualAssertion() {
        assertThrows(IllegalArgumentException.class,
                () -> SoapRedactor.assertNoCredentials(new CapturedXml(SOAP)));
    }
}
