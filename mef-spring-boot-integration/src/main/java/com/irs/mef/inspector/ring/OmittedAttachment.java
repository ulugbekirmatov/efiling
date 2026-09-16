package com.irs.mef.inspector.ring;

/**
 * MIME attachment descriptor. The ZIP is gone — no {@code byte[]} field exists. Do not add one.
 */
public record OmittedAttachment(
        String contentId,
        String contentType,
        long byteLength,
        String sha256) {

    public OmittedAttachment {
        if (byteLength < 0) {
            throw new IllegalArgumentException("attachment length");
        }
        if (sha256 == null || !sha256.matches("^[0-9a-f]{64}$")) {
            throw new IllegalArgumentException("attachment sha256");
        }
        contentId = contentId == null ? "" : contentId;
        contentType = contentType == null ? "" : contentType;
    }
}
