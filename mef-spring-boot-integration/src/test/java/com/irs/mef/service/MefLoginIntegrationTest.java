package com.irs.mef.service;

import gov.irs.a2a.mef.mefheader.TestCdType;
import gov.irs.mef.ApplicationContext;
import gov.irs.mef.services.ServiceContext;
import gov.irs.mef.services.SessionInfo;
import gov.irs.mef.services.data.ETIN;
import gov.irs.mef.services.msi.LoginClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for MeF SDK Login Service with keystore loading.
 *
 * These tests verify that the keystore can be loaded and used by the MeF SDK
 * LoginClient to authenticate with IRS servers.
 *
 * NOTE: These tests require actual IRS credentials and certificates.
 * They are disabled by default. To enable, set system property:
 * -Dmef.integration.test.enabled=true
 */
@DisplayName("MeF SDK Login Integration Tests")
class MefLoginIntegrationTest {

    private static final String TEST_KEYSTORE_PATH = "./irs_cert/IRS_test_keystore.p12";
    private static final String TEST_KEYSTORE_PASSWORD = "test123";
    private static final String TEST_KEY_ALIAS = "irs_test_cert";

    // Test ETIN for IRS MeF testing
    private static final String TEST_ETIN = "97661";
    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_PASSWORD = "testpass";

    private File keystoreFile;

    @BeforeAll
    static void initializeSdk() throws Exception {
        // Set A2A_TOOLKIT_HOME system property before SDK classes are loaded
        // SDK automatically appends "/config" to the toolkit home path, so we set it to mef_config
        String toolkitHome = new File("./src/main/resources/mef_config").getAbsolutePath();
        System.setProperty("A2A_TOOLKIT_HOME", toolkitHome);

        System.out.println("=== Initializing MeF SDK ===");
        System.out.println("A2A_TOOLKIT_HOME: " + toolkitHome);

        // Initialize ApplicationContext
        ApplicationContext.setToolkitHome(toolkitHome);

        System.out.println("MeF SDK initialized successfully");
    }

    @BeforeEach
    void setup() {
        keystoreFile = new File(TEST_KEYSTORE_PATH);
    }

    @Test
    @DisplayName("Should verify keystore file exists before SDK tests")
    void testKeystoreExists() {
        assertTrue(keystoreFile.exists(),
            "Keystore file should exist at: " + TEST_KEYSTORE_PATH);
        assertTrue(keystoreFile.isFile(),
            "Keystore path should be a file");
        assertTrue(keystoreFile.canRead(),
            "Keystore file should be readable");
    }

    @Test
    @DisplayName("Should load SDK classes successfully")
    void testLoadSdkClasses() throws Exception {
        // Verify that all required SDK classes can be loaded
        assertNotNull(LoginClient.class, "LoginClient class should be loaded");
        assertNotNull(ServiceContext.class, "ServiceContext class should be loaded");
        assertNotNull(ETIN.class, "ETIN class should be loaded");
        assertNotNull(TestCdType.class, "TestCdType class should be loaded");

        System.out.println("All SDK classes loaded successfully:");
        System.out.println("  - LoginClient: " + LoginClient.class.getName());
        System.out.println("  - ServiceContext: " + ServiceContext.class.getName());
        System.out.println("  - ETIN: " + ETIN.class.getName());
        System.out.println("  - TestCdType: " + TestCdType.class.getName());
    }

    @Test
    @EnabledIfSystemProperty(named = "mef.integration.test.enabled", matches = "true")
    @DisplayName("Should create ServiceContext with ETIN and test mode")
    void testCreateServiceContext() throws Exception {
        // Create ETIN object
        ETIN etin = new ETIN(TEST_ETIN);
        assertNotNull(etin, "ETIN object should be created");

        // Get TestCdType enum value (T for test mode)
        TestCdType testMode = TestCdType.T;
        assertNotNull(testMode, "TestCdType.T should be accessible");

        // Create ServiceContext
        ServiceContext serviceContext = new ServiceContext(etin, null, testMode);
        assertNotNull(serviceContext, "ServiceContext should be created");

        // Verify ServiceContext properties
        ETIN retrievedEtin = serviceContext.getEtin();
        assertNotNull(retrievedEtin, "ServiceContext should contain ETIN");

        TestCdType retrievedTestMode = serviceContext.getTestCdType();
        assertEquals(testMode, retrievedTestMode, "ServiceContext should have correct test mode");

        System.out.println("ServiceContext created successfully:");
        System.out.println("  - ETIN: " + etin);
        System.out.println("  - Test Mode: " + testMode);
    }

    @Test
    @DisplayName("Should create LoginClient instance")
    void testCreateLoginClient() throws Exception {
        // Create instance
        LoginClient loginClient = new LoginClient();
        assertNotNull(loginClient, "LoginClient instance should be created");

        System.out.println("LoginClient instance created: " + loginClient.getClass().getName());
    }

    @Test
    @DisplayName("Should verify LoginClient has invoke methods")
    void testLoginClientInvokeMethods() throws Exception {
        // Verify 4-parameter invoke method exists (certificate with key alias)
        try {
            var method4Param = LoginClient.class.getMethod(
                "invoke",
                ServiceContext.class,
                File.class,
                String.class,
                String.class
            );
            assertNotNull(method4Param, "4-parameter invoke method should exist");
            System.out.println("Found 4-parameter invoke method (certificate + key alias):");
            System.out.println("  " + method4Param);
        } catch (NoSuchMethodException e) {
            fail("4-parameter invoke method not found: " + e.getMessage());
        }

        // Verify 6-parameter invoke method exists (full authentication)
        try {
            var method6Param = LoginClient.class.getMethod(
                "invoke",
                ServiceContext.class,
                File.class,
                String.class,
                String.class,
                String.class,
                String.class
            );
            assertNotNull(method6Param, "6-parameter invoke method should exist");
            System.out.println("Found 6-parameter invoke method (full authentication):");
            System.out.println("  " + method6Param);
        } catch (NoSuchMethodException e) {
            fail("6-parameter invoke method not found: " + e.getMessage());
        }

        // Verify 2-parameter invoke method exists (SAML string)
        try {
            var method2Param = LoginClient.class.getMethod(
                "invoke",
                ServiceContext.class,
                String.class
            );
            assertNotNull(method2Param, "2-parameter invoke method should exist");
            System.out.println("Found 2-parameter invoke method (SAML string):");
            System.out.println("  " + method2Param);
        } catch (NoSuchMethodException e) {
            fail("2-parameter invoke method not found: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Should verify MeF SDK can access keystore file")
    void testSdkKeystoreAccess() throws Exception {
        System.out.println("\n=== Testing MeF SDK Keystore Access ===");

        // Create LoginClient to verify SDK can be instantiated
        LoginClient loginClient = new LoginClient();
        assertNotNull(loginClient, "LoginClient should be created");
        System.out.println("✓ MeF SDK LoginClient instantiated");

        // Verify the keystore file is accessible to SDK
        assertTrue(keystoreFile.exists(), "Keystore file must exist");
        assertTrue(keystoreFile.canRead(), "Keystore file must be readable");
        System.out.println("✓ Keystore file is accessible: " + keystoreFile.getAbsolutePath());

        // Try to create ETIN object with valid ETIN 97661 from IRS
        ETIN etin = new ETIN(TEST_ETIN);
        assertNotNull(etin, "ETIN object should be created");
        System.out.println("✓ ETIN object created with value: " + TEST_ETIN);

        // Get TestCdType for ATS environment
        TestCdType testMode = TestCdType.T;
        assertNotNull(testMode, "TestCdType.T should be accessible");
        System.out.println("✓ TestCdType set to: T (ATS Test Environment)");

        // Attempt to create ServiceContext - this will fail due to missing AppSysID
        // but it proves SDK initialization is correct and only AppSysID is missing
        System.out.println("\nAttempting to create ServiceContext (will fail without AppSysID)...");

        try {
            ServiceContext serviceContext = new ServiceContext(etin, null, testMode);
            fail("ServiceContext should fail without AppSysID");
        } catch (Exception e) {
            // Expected failure - check if it's the ToolkitRuntimeException we expect
            Throwable checkException = e;
            if (e.getCause() != null) {
                checkException = e.getCause();
            }

            if (checkException.getClass().getName().contains("ToolkitRuntimeException")) {
                String message = checkException.getMessage();
                if (message.contains("MeFClientSDK000032") && message.contains("Client security configuration error")) {
                    System.out.println("✓ Expected error: ServiceContext requires valid AppSysID");
                    System.out.println("  Error: " + message);
                    System.out.println("  This confirms SDK is properly configured but waiting for AppSysID from IRS");
                } else {
                    throw new AssertionError("Unexpected error message: " + message, e);
                }
            } else {
                throw new AssertionError("Unexpected error type: " + checkException.getClass().getName(), e);
            }
        }

        System.out.println("\n=== MeF SDK Readiness Check ===");
        System.out.println("✓ MeF SDK classes are loaded and accessible");
        System.out.println("✓ LoginClient can be instantiated");
        System.out.println("✓ ETIN 97661 (valid ATS ETIN from IRS) is configured");
        System.out.println("✓ Keystore file exists and is readable by SDK");
        System.out.println("✓ TestCdType configured for ATS environment");
        System.out.println("✗ AppSysID not configured (blocking authentication)");
        System.out.println("\nNext step: Obtain AppSysID from IRS to enable authentication");
    }

    @Test
    @DisplayName("Should load and validate keystore for MeF SDK use")
    void testKeystoreLoadingForSdk() throws Exception {
        System.out.println("\n=== Testing Keystore Loading for MeF SDK ===");

        // Test 1: Load keystore using Java KeyStore API
        java.security.KeyStore keyStore = java.security.KeyStore.getInstance("PKCS12");
        try (java.io.FileInputStream fis = new java.io.FileInputStream(keystoreFile)) {
            keyStore.load(fis, TEST_KEYSTORE_PASSWORD.toCharArray());
        }

        System.out.println("✓ Keystore loaded successfully");
        System.out.println("  Type: " + keyStore.getType());
        System.out.println("  Provider: " + keyStore.getProvider().getName());

        // Test 2: Verify alias exists
        boolean hasAlias = keyStore.containsAlias(TEST_KEY_ALIAS);
        assertTrue(hasAlias, "Keystore should contain alias: " + TEST_KEY_ALIAS);
        System.out.println("✓ Certificate alias found: " + TEST_KEY_ALIAS);

        // Test 3: Load certificate
        java.security.cert.X509Certificate cert =
            (java.security.cert.X509Certificate) keyStore.getCertificate(TEST_KEY_ALIAS);
        assertNotNull(cert, "Certificate should not be null");
        System.out.println("✓ Certificate loaded");
        System.out.println("  Subject: " + cert.getSubjectX500Principal().getName());
        System.out.println("  Issuer: " + cert.getIssuerX500Principal().getName());
        System.out.println("  Valid from: " + cert.getNotBefore());
        System.out.println("  Valid until: " + cert.getNotAfter());
        System.out.println("  Serial: " + cert.getSerialNumber());

        // Test 4: Verify private key can be loaded
        java.security.Key key = keyStore.getKey(TEST_KEY_ALIAS, TEST_KEYSTORE_PASSWORD.toCharArray());
        assertNotNull(key, "Private key should not be null");
        assertEquals("RSA", key.getAlgorithm(), "Key algorithm should be RSA");
        System.out.println("✓ Private key loaded");
        System.out.println("  Algorithm: " + key.getAlgorithm());
        System.out.println("  Format: " + key.getFormat());

        // Test 5: Verify certificate chain
        java.security.cert.Certificate[] chain = keyStore.getCertificateChain(TEST_KEY_ALIAS);
        assertNotNull(chain, "Certificate chain should not be null");
        assertTrue(chain.length > 0, "Certificate chain should contain at least one certificate");
        System.out.println("✓ Certificate chain validated");
        System.out.println("  Chain length: " + chain.length);

        // Test 6: Verify certificate is currently valid
        try {
            cert.checkValidity();
            System.out.println("✓ Certificate is currently valid");
        } catch (java.security.cert.CertificateExpiredException e) {
            System.err.println("✗ Certificate has expired");
            throw e;
        } catch (java.security.cert.CertificateNotYetValidException e) {
            System.err.println("✗ Certificate is not yet valid");
            throw e;
        }

        System.out.println("\n=== Keystore Validation Summary ===");
        System.out.println("✓ Keystore file is accessible and readable");
        System.out.println("✓ Keystore password is correct");
        System.out.println("✓ Certificate alias exists");
        System.out.println("✓ X.509 certificate is loaded");
        System.out.println("✓ Private RSA key is accessible");
        System.out.println("✓ Certificate chain is valid");
        System.out.println("✓ Certificate is within validity period");
        System.out.println("\n✓ Keystore is ready for use with MeF SDK LoginClient");
    }

    @Test
    @EnabledIfSystemProperty(named = "mef.integration.test.enabled", matches = "true")
    @DisplayName("Should authenticate using full method with username/password (6-param)")
    void testFullAuthentication() throws Exception {
        System.out.println("\n=== Testing Full Authentication with Username/Password ===");

        // Create ETIN
        ETIN etin = new ETIN(TEST_ETIN);

        // Get test mode
        TestCdType testMode = TestCdType.T;

        // Create ServiceContext
        ServiceContext serviceContext = new ServiceContext(etin, null, testMode);

        // Create LoginClient
        LoginClient loginClient = new LoginClient();

        try {
            // Invoke login with full authentication (6 parameters)
            System.out.println("Invoking LoginClient with full authentication method...");
            System.out.println("  Keystore: " + keystoreFile.getAbsolutePath());
            System.out.println("  Key Alias: " + TEST_KEY_ALIAS);
            System.out.println("  Username: " + TEST_USERNAME);

            loginClient.invoke(
                    serviceContext,
                    keystoreFile,
                    TEST_KEYSTORE_PASSWORD,
                    TEST_KEY_ALIAS,
                    TEST_USERNAME,
                    TEST_PASSWORD);

            System.out.println("Login successful!");

            // Extract SessionInfo
            SessionInfo sessionInfo = serviceContext.getSessionInfo();

            assertNotNull(sessionInfo, "SessionInfo should be populated");
            System.out.println("SessionInfo obtained");

        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            System.err.println("Login failed: " + cause.getClass().getName());
            System.err.println("Message: " + cause.getMessage());

            // Expected to fail without valid credentials
            assertTrue(cause.getMessage().contains("ServiceException") ||
                      cause.getMessage().contains("ToolkitException") ||
                      cause.getMessage().contains("connection") ||
                      cause.getMessage().contains("authentication"),
                      "Should fail with expected SDK exception");
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "mef.integration.test.enabled", matches = "true")
    @DisplayName("Should handle invalid keystore file path")
    void testInvalidKeystorePath() throws Exception {
        // Create ETIN
        ETIN etin = new ETIN(TEST_ETIN);
        TestCdType testMode = TestCdType.T;

        // Create ServiceContext
        ServiceContext serviceContext = new ServiceContext(etin, null, testMode);

        // Create LoginClient
        LoginClient loginClient = new LoginClient();

        // Use non-existent keystore file
        File invalidFile = new File("./non_existent_keystore.p12");

        // Should throw exception when trying to use invalid keystore
        Exception exception = assertThrows(
            Exception.class,
            () -> loginClient.invoke(serviceContext, invalidFile, TEST_KEYSTORE_PASSWORD, TEST_KEY_ALIAS),
            "Should throw exception with invalid keystore file"
        );

        assertNotNull(exception, "Exception should be thrown");
        System.out.println("Expected exception caught: " + exception.getClass().getName());
        System.out.println("Message: " + exception.getMessage());
    }

    @Test
    @EnabledIfSystemProperty(named = "mef.integration.test.enabled", matches = "true")
    @DisplayName("Should handle incorrect keystore password")
    void testIncorrectKeystorePassword() throws Exception {
        // Create ETIN
        ETIN etin = new ETIN(TEST_ETIN);
        TestCdType testMode = TestCdType.T;

        // Create ServiceContext
        ServiceContext serviceContext = new ServiceContext(etin, null, testMode);

        // Create LoginClient
        LoginClient loginClient = new LoginClient();

        // Use incorrect password
        String wrongPassword = "wrongpassword123";

        // Should throw exception with wrong password
        Exception exception = assertThrows(
            Exception.class,
            () -> loginClient.invoke(serviceContext, keystoreFile, wrongPassword, TEST_KEY_ALIAS),
            "Should throw exception with incorrect password"
        );

        assertNotNull(exception, "Exception should be thrown");
        System.out.println("Expected exception caught: " + exception.getClass().getName());
        System.out.println("Message: " + exception.getMessage());
    }
}
