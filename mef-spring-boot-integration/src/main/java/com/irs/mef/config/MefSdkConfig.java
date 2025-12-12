package com.irs.mef.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;

/**
 * Configuration class for MeF Client SDK.
 * Binds application.yml properties to Java objects for SDK configuration.
 *
 * This class manages all MeF SDK-related configuration including:
 * - Toolkit home directory
 * - Environment selection (ATS/PRD)
 * - Certificate and keystore settings
 * - Authentication credentials
 * - Service endpoints
 * - Transport and security settings
 */
@Configuration
@ConfigurationProperties(prefix = "mef.sdk")
@Data
public class MefSdkConfig {

    /**
     * Location of SDK configuration files (A2A_TOOLKIT_HOME)
     */
    private String toolkitHome;

    /**
     * Environment: ATS (test) or PRD (production)
     */
    private String environment = "ATS";

    /**
     * Transport configuration
     */
    private Transport transport = new Transport();

    /**
     * Audit logging configuration
     */
    private Audit audit = new Audit();

    /**
     * SSL/TLS configuration
     */
    private Ssl ssl = new Ssl();

    /**
     * Client certificate configuration
     */
    private Certificate certificate = new Certificate();

    /**
     * Authentication credentials
     */
    private Authentication authentication = new Authentication();

    /**
     * Service endpoints
     */
    private Endpoints endpoints = new Endpoints();

    /**
     * WS-Security configuration
     */
    private Security security = new Security();

    @PostConstruct
    public void init() {
        // Initialize A2A_TOOLKIT_HOME using ApplicationContext if available
        if (toolkitHome != null && !toolkitHome.isEmpty()) {
            try {
                // Try to set it programmatically if SDK supports it
                // gov.irs.mef.ApplicationContext.setToolkitHome(toolkitHome);
                System.out.println("MeF SDK Configuration Initialized");
                System.out.println("Environment: " + environment);
                System.out.println("Toolkit Home: " + toolkitHome);
            } catch (Exception e) {
                System.err.println("Warning: Could not initialize MeF SDK ApplicationContext: " + e.getMessage());
            }
        }
    }

    /**
     * Check if all required certificate configurations are provided
     */
    public boolean isCertificateConfigured() {
        return certificate != null &&
               certificate.getKeystorePath() != null &&
               !certificate.getKeystorePath().isEmpty() &&
               certificate.getKeystorePassword() != null &&
               !certificate.getKeystorePassword().isEmpty();
    }

    /**
     * Check if authentication credentials are provided for certificate-only authentication.
     * Requires ETIN and ASID (used with X.509 client certificate).
     */
    public boolean isAuthenticationConfigured() {
        return authentication != null &&
               authentication.getEtin() != null &&
               !authentication.getEtin().isEmpty() &&
               authentication.getAsid() != null &&
               !authentication.getAsid().isEmpty();
    }

    /**
     * Get the current environment's base URL
     */
    public String getBaseUrl() {
        if ("PRD".equalsIgnoreCase(environment)) {
            return endpoints.getPrd().getBaseUrl();
        }
        return endpoints.getAts().getBaseUrl();
    }

    @Data
    public static class Transport {
        private int connectTimeoutSeconds = 600;
        private int readTimeoutSeconds = 600;
        private boolean httpChunkingEnabled = true;
        private int httpChunkSize = 8192;
    }

    @Data
    public static class Audit {
        private String logFile = "logs/mef_audit_log.txt";
        private int maxFileSizeMb = 10;
    }

    @Data
    public static class Ssl {
        private String trustStorePath;
        private String trustStorePassword = "changeit";
        private String trustStoreType = "JKS";
    }

    @Data
    public static class Certificate {
        private String keystorePath;
        private String keystorePassword;
        private String keystoreType = "PKCS12";
        private String keyAlias;
        private String keyPassword;
        private boolean useWindowsKeystore = false;
    }

    @Data
    public static class Authentication {
        private String etin;
        private String efin;
        private String username;
        private String password;
        private String asid;
    }

    @Data
    public static class Endpoints {
        private EndpointConfig ats = new EndpointConfig();
        private EndpointConfig prd = new EndpointConfig();
    }

    @Data
    public static class EndpointConfig {
        private String baseUrl;
        private String login;
        private String logout;
        private String sendSubmissions;
        private String getSubmissionStatus;
        private String getNewSubmissionsStatus;
        private String getAck;
        private String getNewAcks;
        private String getAcks;

        public String getFullUrl(String endpoint) {
            return baseUrl + endpoint;
        }
    }

    @Data
    public static class Security {
        private String signatureAlgorithm;
        private String digestAlgorithm;
        private String canonicalizationAlgorithm;
        private String tokenEncodingType;
        private String tokenValueType;
    }
}
