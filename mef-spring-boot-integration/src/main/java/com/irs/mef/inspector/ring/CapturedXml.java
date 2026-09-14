package com.irs.mef.inspector.ring;

import com.irs.mef.inspector.Sha256Hex;
import com.irs.mef.inspector.capture.PrettyXml;

/**
 * Original document as packed into SubmissionXML / SubmissionManifest.
 * pretty() is display-only. Hashing pretty() is a bug — the ring hashes original().
 */
public record CapturedXml(String original) {

    public CapturedXml {
        if (original == null || original.isBlank()) {
            throw new IllegalArgumentException("captured xml is empty");
        }
    }

    public String sha256() {
        return Sha256Hex.ofUtf8(original);
    }

    public String pretty() {
        return PrettyXml.indent(original);
    }
}
