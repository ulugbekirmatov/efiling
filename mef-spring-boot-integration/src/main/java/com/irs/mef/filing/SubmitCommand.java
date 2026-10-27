package com.irs.mef.filing;


import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * A fully-validated submit request. Constructed only at the HTTP boundary; trusted everywhere inside.
 * The return XML is caller-supplied content — never a server file path.
 */
public record SubmitCommand(
        String clientRequestId,
        String clientId,
        Ein clientEin,
        FormType formType,
        TaxPeriod taxPeriod,
        String returnXml,
        boolean allowDuplicatePeriod) {

    public SubmitCommand {
        if (clientRequestId == null || clientRequestId.isBlank() || clientRequestId.length() > 128) {
            throw new FilingValidationException("Idempotency-Key",
                    "Idempotency-Key must be non-blank and at most 128 characters");
        }
        if (clientId == null || clientId.isBlank() || clientId.length() > 64) {
            throw new FilingValidationException("clientId", "clientId must be non-blank and at most 64 characters");
        }
        if (returnXml == null || returnXml.isBlank()) {
            throw new FilingValidationException("returnXml", "returnXml is required");
        }
    }

    /** SHA-256 of the return document. Journaled for payload-fingerprinting; the raw XML never is (wage PII). */
    public String returnXmlSha256() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(returnXml.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
