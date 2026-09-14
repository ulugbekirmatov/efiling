package com.irs.mef.inspector;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 helpers. Lowercase hex, same as {@code NewSendSubmitCommand.returnXmlSha256}. */
public final class Sha256Hex {

    private Sha256Hex() {
    }

    public static String ofUtf8(String text) {
        return of(text.getBytes(StandardCharsets.UTF_8));
    }

    public static String of(byte[] bytes) {
        return HexFormat.of().formatHex(digest().digest(bytes));
    }

    public static Digest ofBytes(byte[] bytes) {
        return new Digest(bytes.length, of(bytes));
    }

    /** Digests the stream without retaining payload bytes. */
    public static Digest of(InputStream in) throws IOException {
        MessageDigest digest = digest();
        long length = 0L;
        try (DigestInputStream digester = new DigestInputStream(in, digest)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = digester.read(buf)) >= 0) {
                length += n;
            }
        }
        return new Digest(length, HexFormat.of().formatHex(digest.digest()));
    }

    public record Digest(long byteLength, String hex) {
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
