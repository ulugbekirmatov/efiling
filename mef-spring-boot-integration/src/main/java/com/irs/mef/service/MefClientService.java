package com.irs.mef.service;

import com.irs.mef.config.MefSdkConfig;
import com.irs.mef.dto.LoginResponse;
import com.irs.mef.exception.MefException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.reflect.Method;
import java.security.KeyStore;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import javax.security.auth.x500.X500Principal;
import java.security.cert.X509Certificate;

/**
 * Core service for MeF Client SDK operations.
 * Handles initialization, login/logout, and session management.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MefClientService {

    private final MefSdkConfig mefConfig;

    // Session state
    private String currentSamlAssertion;
    private String currentSessionId;
    private boolean isLoggedIn = false;
    private Object currentServiceContext; // ServiceContext loaded via reflection

    @PostConstruct
    public void init() {
        log.info("Initializing MeF Client Service");
        log.info("Environment: {}", mefConfig.getEnvironment());
        log.info("Base URL: {}", mefConfig.getBaseUrl());

        // Validate configuration
        if (!mefConfig.isCertificateConfigured()) {
            log.warn("Certificate not configured. Please configure keystore path and password.");
        }
        if (!mefConfig.isAuthenticationConfigured()) {
            log.warn("Authentication credentials not configured. Please configure ETIN, username, and password.");
        }

        // TODO: Initialize MeF SDK ApplicationContext
        // Example:
        // try {
        //     gov.irs.mef.ApplicationContext.setToolkitHome(mefConfig.getToolkitHome());
        //     log.info("MeF SDK ApplicationContext initialized");
        // } catch (Exception e) {
        //     throw new MefException("Failed to initialize MeF SDK", e);
        // }
    }

    /**
     * Login to IRS MeF A2A services using environment configuration.
     * Uses reflection to load MeF SDK classes to avoid Java module access issues.
     *
     * @return Login response with SAML assertion
     */
    public LoginResponse login() {
        // Read ETIN from configuration
        String etin = mefConfig.getAuthentication().getEtin();
        log.info("Attempting login for ETIN: {}", etin);

        try {
            log.info("Calling IRS Login service with ETIN: {} using certificate-only authentication",
                    etin != null ? etin : "null");

            // Load SDK classes via reflection
            Class<?> etinClass = Class.forName("gov.irs.mef.services.data.ETIN");
            Class<?> testCdTypeClass = Class.forName("gov.irs.a2a.mef.mefheader.TestCdType");
            Class<?> serviceContextClass = Class.forName("gov.irs.mef.services.ServiceContext");
            Class<?> loginClientClass = Class.forName("gov.irs.mef.services.msi.LoginClient");

            // Create ETIN object (can be null for testing without credentials)
            Object etinObj = null;
            if (etin != null && !etin.isEmpty()) {
                etinObj = etinClass.getConstructor(String.class).newInstance(etin);
            }

            // Get TestCdType enum value from environment (ATS = test, PRD = production)
            boolean isProduction = "PRD".equalsIgnoreCase(mefConfig.getEnvironment());
            Object testMode = isProduction
                ? Enum.valueOf((Class<Enum>) testCdTypeClass, "P")
                : Enum.valueOf((Class<Enum>) testCdTypeClass, "T");
            log.info("Using environment: {} (TestCdType: {})", mefConfig.getEnvironment(), testMode);

            // Get ASID (Application System ID) from configuration
            String asid = mefConfig.getAuthentication().getAsid();
            if (asid == null || asid.trim().isEmpty()) {
                log.warn("ASID not configured - authentication may fail");
            } else {
                log.info("Using ASID: {}", asid);
            }

            // Create ServiceContext with ASID via reflection
            Object serviceContext = serviceContextClass
                .getConstructor(etinClass, String.class, testCdTypeClass)
                .newInstance(etinObj, asid, testMode);

            // Load certificate file from configuration
            File keystoreFile = null;
            String keystorePassword = null;
            String keyAlias = null;

            if (mefConfig.isCertificateConfigured()) {
                String keystorePath = mefConfig.getCertificate().getKeystorePath();
                keystoreFile = new File(keystorePath);

                // Validate keystore file exists
                if (!keystoreFile.exists() || !keystoreFile.isFile()) {
                    throw new MefException("KEYSTORE_NOT_FOUND",
                            "Keystore file not found",
                            "Keystore file does not exist at: " + keystorePath);
                }

                keystorePassword = mefConfig.getCertificate().getKeystorePassword();
                keyAlias = mefConfig.getCertificate().getKeyAlias();

                log.info("Using keystore: {}", keystorePath);
                log.info("Using certificate-only authentication with key alias: {}", keyAlias);
            } else {
                log.warn("Certificate not configured - authentication may fail with production IRS servers");
            }

            // Create LoginClient and invoke login via reflection
            // Method signature: invoke(ServiceContext, File keystoreFile, String keystorePassword, String keyAlias)
            log.info("Invoking IRS MeF Login service...");
            Object loginClient = loginClientClass.getDeclaredConstructor().newInstance();
            Method invokeMethod = loginClientClass.getMethod("invoke",
                serviceContextClass, File.class, String.class, String.class);
            invokeMethod.invoke(loginClient, serviceContext, keystoreFile, keystorePassword, keyAlias);

            // Extract SAML assertion from ServiceContext SessionInfo via reflection
            Method getSessionInfoMethod = serviceContextClass.getMethod("getSessionInfo");
            Object sessionInfo = getSessionInfoMethod.invoke(serviceContext);

            if (sessionInfo == null) {
                throw new MefException("LOGIN_FAILED",
                        "Login failed - no session info returned",
                        "SessionInfo is null after login");
            }

            // Get SAML token from SessionInfo (returns org.w3c.dom.Element) via reflection
            Class<?> sessionInfoClass = Class.forName("gov.irs.mef.services.SessionInfo");
            Method getSAMLTokenMethod = sessionInfoClass.getMethod("getSAMLToken");
            org.w3c.dom.Element samlTokenElement = (org.w3c.dom.Element) getSAMLTokenMethod.invoke(sessionInfo);

            if (samlTokenElement == null) {
                throw new MefException("LOGIN_FAILED",
                        "Login failed - no SAML token returned",
                        "SAML token is null after login");
            }

            // Convert SAML Element to String for storage
            String samlAssertion = convertElementToString(samlTokenElement);

            // Extract SAML Assertion ID from the token (this is the real session identifier from IRS)
            String samlAssertionId = extractSamlAssertionId(samlTokenElement);

            currentSamlAssertion = samlAssertion;
            currentSessionId = samlAssertionId != null ? samlAssertionId : "SESSION_" + System.currentTimeMillis();
            currentServiceContext = serviceContext;
            isLoggedIn = true;

            log.info("Login successful for ETIN: {}", etin);
            log.info("Session ID (SAML Assertion ID): {}", currentSessionId);
            log.debug("SAML assertion obtained: {} characters", samlAssertion != null ? samlAssertion.length() : 0);

            return LoginResponse.builder()
                    .success(true)
                    .samlAssertion(currentSamlAssertion)
                    .sessionId(currentSessionId)
                    .timestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
                    .message("Login successful")
                    .build();

        } catch (Exception e) {
            log.error("Login failed for ETIN: {}", etin, e);

            // Extract the actual error message from IRS
            String errorMessage = e.getMessage();
            Throwable cause = e.getCause();
            while (cause != null) {
                if (cause.getMessage() != null) {
                    errorMessage = cause.getMessage();
                }
                cause = cause.getCause();
            }

            log.error("IRS Error Response: {}", errorMessage, e);
            throw new MefException("LOGIN_FAILED", "Login to IRS MeF failed", errorMessage != null ? errorMessage : e.toString(), e);
        }
    }

    /**
     * Logout from IRS MeF A2A services.
     *
     * @return true if logout successful
     */
    public boolean logout() {
        log.info("Attempting logout");

        if (!isLoggedIn) {
            log.warn("No active session to logout");
            return false;
        }

        try {
            // TODO: Implement actual MeF SDK logout call
            // Example code structure:
            //
            // LogoutServiceType logoutService = new LogoutServiceType();
            //
            // // Set SAML assertion from login
            // Map<String, Object> requestContext = ((BindingProvider) logoutService).getRequestContext();
            // requestContext.put("saml.assertion", currentSamlAssertion);
            //
            // // Invoke logout service
            // Logout logoutRequest = new Logout();
            // LogoutResponse logoutResponse = logoutService.logout(logoutRequest);

            // PLACEHOLDER - Replace with actual SDK call
            currentSamlAssertion = null;
            currentSessionId = null;
            currentServiceContext = null;
            isLoggedIn = false;

            log.info("Logout successful");
            return true;

        } catch (Exception e) {
            log.error("Logout failed", e);
            throw new MefException("LOGOUT_FAILED", "Logout from IRS MeF failed", e.getMessage(), e);
        }
    }

    /**
     * Check if currently logged in to IRS MeF.
     */
    public boolean isLoggedIn() {
        return isLoggedIn;
    }

    /**
     * Get current SAML assertion (for use by other services).
     */
    public String getCurrentSamlAssertion() {
        if (!isLoggedIn) {
            throw new MefException("NOT_LOGGED_IN", "Not logged in to IRS MeF", "Please login first");
        }
        return currentSamlAssertion;
    }

    /**
     * Get current session ID.
     */
    public String getCurrentSessionId() {
        return currentSessionId;
    }

    /**
     * Get current ServiceContext (for use by other services).
     * Returns Object to avoid static SDK class dependency.
     */
    public Object getCurrentServiceContext() {
        if (!isLoggedIn) {
            throw new MefException("NOT_LOGGED_IN", "Not logged in to IRS MeF", "Please login first");
        }
        return currentServiceContext;
    }

    /**
     * Convert org.w3c.dom.Element to String (for SAML assertion storage).
     */
    private String convertElementToString(org.w3c.dom.Element element) {
        try {
            javax.xml.transform.TransformerFactory tf = javax.xml.transform.TransformerFactory.newInstance();
            javax.xml.transform.Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "yes");
            java.io.StringWriter writer = new java.io.StringWriter();
            transformer.transform(new javax.xml.transform.dom.DOMSource(element),
                                new javax.xml.transform.stream.StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            log.error("Failed to convert SAML Element to String", e);
            throw new MefException("SAML_CONVERSION_ERROR",
                    "Failed to convert SAML token to string",
                    e.getMessage(), e);
        }
    }

    /**
     * Extract SAML Assertion ID from the SAML token Element.
     * SAML assertions have an ID attribute (e.g., <saml:Assertion ID="...">).
     * This ID is the real session identifier provided by IRS.
     *
     * @param samlTokenElement The SAML token Element from IRS
     * @return The SAML Assertion ID, or null if not found
     */
    private String extractSamlAssertionId(org.w3c.dom.Element samlTokenElement) {
        try {
            // SAML 2.0 assertions use "ID" attribute
            String assertionId = samlTokenElement.getAttribute("ID");
            if (assertionId != null && !assertionId.isEmpty()) {
                log.debug("Extracted SAML Assertion ID: {}", assertionId);
                return assertionId;
            }

            // SAML 1.x assertions use "AssertionID" attribute (fallback)
            assertionId = samlTokenElement.getAttribute("AssertionID");
            if (assertionId != null && !assertionId.isEmpty()) {
                log.debug("Extracted SAML 1.x Assertion ID: {}", assertionId);
                return assertionId;
            }

            log.warn("SAML Assertion ID not found in token");
            return null;

        } catch (Exception e) {
            log.error("Failed to extract SAML Assertion ID", e);
            return null;
        }
    }

    /**
     * Layered certificate validation test.
     * Tests certificate loading without full ServiceContext/authentication.
     *
     * @param performFullAuthTest Whether to perform full authentication test (Level 3)
     * @return Certificate test response with results from each layer
     */
    public com.irs.mef.dto.CertificateTestResponse testCertificate(boolean performFullAuthTest) {
        log.info("Starting layered certificate validation test");

        com.irs.mef.dto.CertificateTestResponse.CertificateTestResponseBuilder responseBuilder =
                com.irs.mef.dto.CertificateTestResponse.builder()
                        .timestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));

        // Level 1: Load Keystore
        com.irs.mef.dto.CertificateTestResponse.KeystoreLoadResult keystoreResult =
                testKeystoreLoading();
        responseBuilder.keystoreLoadResult(keystoreResult);

        if (!keystoreResult.isSuccess()) {
            log.error("Certificate test failed at Level 1 (Keystore Loading)");
            return responseBuilder
                    .success(false)
                    .errorMessage("Failed at Level 1: " + keystoreResult.getError())
                    .build();
        }

        // Level 2: Extract Certificate Details
        com.irs.mef.dto.CertificateTestResponse.CertificateDetailsResult detailsResult =
                testCertificateDetails();
        responseBuilder.certificateDetailsResult(detailsResult);

        if (!detailsResult.isSuccess()) {
            log.error("Certificate test failed at Level 2 (Certificate Details)");
            return responseBuilder
                    .success(false)
                    .errorMessage("Failed at Level 2: " + detailsResult.getError())
                    .build();
        }

        // Level 3: Full Authentication Test (optional)
        if (performFullAuthTest) {
            com.irs.mef.dto.CertificateTestResponse.AuthenticationTestResult authResult =
                    testFullAuthentication();
            responseBuilder.authenticationTestResult(authResult);

            if (!authResult.isSuccess()) {
                log.warn("Certificate test failed at Level 3 (Full Authentication)");
                return responseBuilder
                        .success(false)
                        .errorMessage("Failed at Level 3: " + authResult.getError())
                        .build();
            }
        }

        log.info("Certificate validation test completed successfully");
        return responseBuilder.success(true).build();
    }

    /**
     * Level 1: Test keystore loading using SDK's KeyStoreUtil via reflection.
     */
    private com.irs.mef.dto.CertificateTestResponse.KeystoreLoadResult testKeystoreLoading() {
        log.info("Level 1: Testing keystore loading...");

        try {
            if (!mefConfig.isCertificateConfigured()) {
                return com.irs.mef.dto.CertificateTestResponse.KeystoreLoadResult.builder()
                        .success(false)
                        .error("Certificate not configured in application.yml")
                        .build();
            }

            String keystorePath = mefConfig.getCertificate().getKeystorePath();
            String keystorePassword = mefConfig.getCertificate().getKeystorePassword();

            File keystoreFile = new File(keystorePath);

            // Validate file exists
            if (!keystoreFile.exists() || !keystoreFile.isFile()) {
                return com.irs.mef.dto.CertificateTestResponse.KeystoreLoadResult.builder()
                        .success(false)
                        .keystorePath(keystorePath)
                        .error("Keystore file not found at: " + keystorePath)
                        .build();
            }

            // Load keystore using SDK's KeyStoreUtil via reflection
            Class<?> keyStoreUtilClass = Class.forName("gov.irs.mef.services.util.KeyStoreUtil");
            Method loadKeyStoreMethod = keyStoreUtilClass.getMethod("loadKeyStore", File.class, char[].class);
            KeyStore keyStore = (KeyStore) loadKeyStoreMethod.invoke(null, keystoreFile, keystorePassword.toCharArray());

            // Get keystore type
            String keystoreType = keyStore.getType();

            log.info("Level 1 PASSED: Keystore loaded successfully");
            return com.irs.mef.dto.CertificateTestResponse.KeystoreLoadResult.builder()
                    .success(true)
                    .keystorePath(keystorePath)
                    .keystoreType(keystoreType)
                    .message("Keystore loaded successfully. Type: " + keystoreType)
                    .build();

        } catch (Exception e) {
            log.error("Level 1 FAILED: Keystore loading failed", e);
            return com.irs.mef.dto.CertificateTestResponse.KeystoreLoadResult.builder()
                    .success(false)
                    .keystorePath(mefConfig.getCertificate().getKeystorePath())
                    .error("Keystore loading failed: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Level 2: Test certificate details extraction using SDK's KeyStoreUtil via reflection.
     */
    @SuppressWarnings("unchecked")
    private com.irs.mef.dto.CertificateTestResponse.CertificateDetailsResult testCertificateDetails() {
        log.info("Level 2: Testing certificate details extraction...");

        try {
            String keystorePath = mefConfig.getCertificate().getKeystorePath();
            String keystorePassword = mefConfig.getCertificate().getKeystorePassword();
            File keystoreFile = new File(keystorePath);

            // Load SDK's KeyStoreUtil via reflection
            Class<?> keyStoreUtilClass = Class.forName("gov.irs.mef.services.util.KeyStoreUtil");
            Method loadKeyStoreMethod = keyStoreUtilClass.getMethod("loadKeyStore", File.class, char[].class);
            Method listPrivateKeyAliasesMethod = keyStoreUtilClass.getMethod("listPrivateKeyAliases", KeyStore.class);
            Method listKeyInfoMethod = keyStoreUtilClass.getMethod("listKeyInfo", KeyStore.class);

            // Load keystore via reflection
            KeyStore keyStore = (KeyStore) loadKeyStoreMethod.invoke(null, keystoreFile, keystorePassword.toCharArray());

            // List private key aliases via reflection
            List<String> aliases = (List<String>) listPrivateKeyAliasesMethod.invoke(null, keyStore);

            // Get key info (alias -> X500Principal mapping) via reflection
            Map<String, X500Principal> keyInfoMap = (Map<String, X500Principal>) listKeyInfoMethod.invoke(null, keyStore);

            // Build certificate info list
            java.util.List<com.irs.mef.dto.CertificateTestResponse.CertificateInfo> certInfoList =
                    new java.util.ArrayList<>();

            for (String alias : aliases) {
                X500Principal principal = keyInfoMap.get(alias);
                if (principal != null) {
                    // Parse X500Principal to extract DN components
                    String subjectDN = principal.getName();

                    // Get certificate for more details
                    X509Certificate cert = (X509Certificate) keyStore.getCertificate(alias);

                    String issuerDN = cert != null ? cert.getIssuerX500Principal().getName() : "Unknown";
                    String serialNumber = cert != null ? cert.getSerialNumber().toString() : "Unknown";

                    // Parse DN components
                    java.util.Map<String, String> dnComponents = parseDN(subjectDN);

                    com.irs.mef.dto.CertificateTestResponse.CertificateInfo certInfo =
                            com.irs.mef.dto.CertificateTestResponse.CertificateInfo.builder()
                                    .alias(alias)
                                    .subjectDN(subjectDN)
                                    .issuerDN(issuerDN)
                                    .serialNumber(serialNumber)
                                    .commonName(dnComponents.get("CN"))
                                    .organization(dnComponents.get("O"))
                                    .organizationalUnit(dnComponents.get("OU"))
                                    .country(dnComponents.get("C"))
                                    .build();

                    certInfoList.add(certInfo);

                    log.info("Certificate found - Alias: {}, CN: {}, O: {}",
                            alias, dnComponents.get("CN"), dnComponents.get("O"));
                }
            }

            log.info("Level 2 PASSED: Found {} certificate(s)", certInfoList.size());
            return com.irs.mef.dto.CertificateTestResponse.CertificateDetailsResult.builder()
                    .success(true)
                    .certificateCount(certInfoList.size())
                    .aliases(aliases)
                    .certificates(certInfoList)
                    .message("Found " + certInfoList.size() + " certificate(s) with private keys")
                    .build();

        } catch (Exception e) {
            log.error("Level 2 FAILED: Certificate details extraction failed", e);
            return com.irs.mef.dto.CertificateTestResponse.CertificateDetailsResult.builder()
                    .success(false)
                    .error("Certificate details extraction failed: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Level 3: Test full authentication with IRS using ServiceContext.
     */
    private com.irs.mef.dto.CertificateTestResponse.AuthenticationTestResult testFullAuthentication() {
        log.info("Level 3: Testing full authentication with IRS...");

        try {
            // Verify ASID is configured before attempting auth
            if (!mefConfig.isAuthenticationConfigured() ||
                mefConfig.getAuthentication().getAsid() == null ||
                mefConfig.getAuthentication().getAsid().trim().isEmpty()) {
                throw new MefException("ASID_NOT_CONFIGURED",
                    "ASID (Application System ID) is not configured",
                    "Please configure MEF_ASID in .env file");
            }

            // Attempt login using environment configuration
            LoginResponse loginResponse = login();

            boolean samlObtained = loginResponse.getSamlAssertion() != null &&
                    !loginResponse.getSamlAssertion().isEmpty();

            log.info("Level 3 PASSED: Full authentication successful, SAML token obtained");
            return com.irs.mef.dto.CertificateTestResponse.AuthenticationTestResult.builder()
                    .success(true)
                    .samlTokenObtained(samlObtained)
                    .message("Full authentication successful with IRS")
                    .build();

        } catch (Exception e) {
            log.error("Level 3 FAILED: Full authentication failed", e);
            return com.irs.mef.dto.CertificateTestResponse.AuthenticationTestResult.builder()
                    .success(false)
                    .samlTokenObtained(false)
                    .error("Full authentication failed: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Parse Distinguished Name (DN) string into components.
     */
    private java.util.Map<String, String> parseDN(String dn) {
        java.util.Map<String, String> components = new java.util.HashMap<>();
        if (dn == null || dn.isEmpty()) {
            return components;
        }

        // Split by comma (but not within quotes)
        String[] parts = dn.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
        for (String part : parts) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2) {
                components.put(kv[0].trim(), kv[1].trim());
            }
        }

        return components;
    }

    @PreDestroy
    public void cleanup() {
        log.info("Cleaning up MeF Client Service");
        if (isLoggedIn) {
            try {
                logout();
            } catch (Exception e) {
                log.error("Error during cleanup logout", e);
            }
        }
    }
}
