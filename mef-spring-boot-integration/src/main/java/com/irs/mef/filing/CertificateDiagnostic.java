package com.irs.mef.filing;

import java.util.List;

/** Offline keystore read. No IRS call. */
public record CertificateDiagnostic(
        boolean success,
        String keystorePath,
        String keystoreType,
        String message,
        List<CertificateInfo> certificates) {

    public CertificateDiagnostic {
        certificates = certificates == null ? List.of() : List.copyOf(certificates);
    }

    public record CertificateInfo(
            String alias,
            String subjectDn,
            String issuerDn,
            String serialNumber,
            String commonName,
            String organization) {}
}
