package com.irs.mef.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Response DTO for certificate validation testing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CertificateTestResponse {

    /**
     * Overall test success status.
     */
    private boolean success;

    /**
     * Timestamp of the test.
     */
    private String timestamp;

    /**
     * Level 1: Keystore loading results.
     */
    private KeystoreLoadResult keystoreLoadResult;

    /**
     * Level 2: Certificate details.
     */
    private CertificateDetailsResult certificateDetailsResult;

    /**
     * Level 3: Full authentication test (optional).
     */
    private AuthenticationTestResult authenticationTestResult;

    /**
     * Error message if test failed.
     */
    private String errorMessage;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KeystoreLoadResult {
        private boolean success;
        private String keystorePath;
        private String keystoreType;
        private String message;
        private String error;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CertificateDetailsResult {
        private boolean success;
        private int certificateCount;
        private List<String> aliases;
        private List<CertificateInfo> certificates;
        private String message;
        private String error;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CertificateInfo {
        private String alias;
        private String subjectDN;
        private String issuerDN;
        private String serialNumber;
        private String commonName;
        private String organization;
        private String organizationalUnit;
        private String country;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthenticationTestResult {
        private boolean success;
        private String message;
        private String error;
        private boolean samlTokenObtained;
    }
}
