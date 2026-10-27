package com.irs.mef.filing;

import java.nio.charset.StandardCharsets;

/**
 * The offline half of a filing: composed return, minted id, rendered manifest.
 * {@link Cleared} is the only token {@code transmit} accepts, and only {@link FilingDesk#preflight}
 * can produce one.
 */
public final class Preflight {

    private final ClientFiling filing;
    private final Filing prepared;
    private final String manifestXml;
    private final Cleared cleared;

    private Preflight(ClientFiling filing, Filing prepared, String manifestXml) {
        this.filing = filing;
        this.prepared = prepared;
        this.manifestXml = manifestXml;
        this.cleared = new Cleared();
    }

    static Preflight of(ClientFiling filing, Filing prepared, String manifestXml) {
        return new Preflight(filing, prepared, manifestXml);
    }

    public ClientFiling filing() {
        return filing;
    }

    public SubmissionId id() {
        return prepared.submissionId();
    }

    public byte[] manifestXml() {
        return manifestXml.getBytes(StandardCharsets.UTF_8);
    }

    public byte[] returnXml() {
        return prepared.command().returnXml().getBytes(StandardCharsets.UTF_8);
    }

    Cleared cleared() {
        return cleared;
    }

    Filing prepared() {
        return prepared;
    }

    /**
     * Private constructor: the enclosing {@link Preflight} is the only type that can construct it,
     * and {@link FilingDesk#preflight} is the only caller of {@link Preflight#of}.
     */
    public final class Cleared {
        private Cleared() {}

        Filing prepared() {
            return prepared;
        }

        String manifestXml() {
            return manifestXml;
        }
    }
}
