package com.irs.mef.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Enumeration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for certificate and keystore loading functionality.
 * These tests verify that PKCS12 keystores can be loaded and accessed correctly.
 */
@DisplayName("Certificate Loading Tests")
class CertificateLoadingTest {

    private static final String TEST_KEYSTORE_PATH = "./irs_cert/IRS_test_keystore.p12";
    private static final String TEST_KEYSTORE_PASSWORD = "test123";
    private static final String TEST_KEY_ALIAS = "irs_test_cert";

    @Test
    @DisplayName("Should load PKCS12 keystore successfully")
    void testLoadKeystore() throws Exception {
        // Given
        File keystoreFile = new File(TEST_KEYSTORE_PATH);

        // When
        KeyStore keyStore = loadKeyStore(keystoreFile, TEST_KEYSTORE_PASSWORD);

        // Then
        assertNotNull(keyStore, "KeyStore should not be null");
        assertEquals("PKCS12", keyStore.getType(), "KeyStore type should be PKCS12");
    }

    @Test
    @DisplayName("Should verify keystore file exists")
    void testKeystoreFileExists() {
        // Given
        File keystoreFile = new File(TEST_KEYSTORE_PATH);

        // Then
        assertTrue(keystoreFile.exists(), "Keystore file should exist at: " + TEST_KEYSTORE_PATH);
        assertTrue(keystoreFile.isFile(), "Keystore path should be a file, not a directory");
        assertTrue(keystoreFile.canRead(), "Keystore file should be readable");
    }

    @Test
    @DisplayName("Should contain expected certificate alias")
    void testKeystoreContainsAlias() throws Exception {
        // Given
        File keystoreFile = new File(TEST_KEYSTORE_PATH);
        KeyStore keyStore = loadKeyStore(keystoreFile, TEST_KEYSTORE_PASSWORD);

        // When
        boolean hasAlias = keyStore.containsAlias(TEST_KEY_ALIAS);

        // Then
        assertTrue(hasAlias, "KeyStore should contain alias: " + TEST_KEY_ALIAS);
    }

    @Test
    @DisplayName("Should load certificate from keystore")
    void testLoadCertificate() throws Exception {
        // Given
        File keystoreFile = new File(TEST_KEYSTORE_PATH);
        KeyStore keyStore = loadKeyStore(keystoreFile, TEST_KEYSTORE_PASSWORD);

        // When
        Certificate cert = keyStore.getCertificate(TEST_KEY_ALIAS);

        // Then
        assertNotNull(cert, "Certificate should not be null");
        assertTrue(cert instanceof X509Certificate, "Certificate should be X509Certificate type");

        X509Certificate x509Cert = (X509Certificate) cert;
        System.out.println("Certificate Subject: " + x509Cert.getSubjectX500Principal().getName());
        System.out.println("Certificate Issuer: " + x509Cert.getIssuerX500Principal().getName());
        System.out.println("Certificate Valid From: " + x509Cert.getNotBefore());
        System.out.println("Certificate Valid Until: " + x509Cert.getNotAfter());
    }

    @Test
    @DisplayName("Should load private key from keystore")
    void testLoadPrivateKey() throws Exception {
        // Given
        File keystoreFile = new File(TEST_KEYSTORE_PATH);
        KeyStore keyStore = loadKeyStore(keystoreFile, TEST_KEYSTORE_PASSWORD);

        // When
        Key key = keyStore.getKey(TEST_KEY_ALIAS, TEST_KEYSTORE_PASSWORD.toCharArray());

        // Then
        assertNotNull(key, "Private key should not be null");
        assertEquals("RSA", key.getAlgorithm(), "Private key algorithm should be RSA");
        System.out.println("Private Key Algorithm: " + key.getAlgorithm());
        System.out.println("Private Key Format: " + key.getFormat());
    }

    @Test
    @DisplayName("Should verify certificate chain")
    void testCertificateChain() throws Exception {
        // Given
        File keystoreFile = new File(TEST_KEYSTORE_PATH);
        KeyStore keyStore = loadKeyStore(keystoreFile, TEST_KEYSTORE_PASSWORD);

        // When
        Certificate[] chain = keyStore.getCertificateChain(TEST_KEY_ALIAS);

        // Then
        assertNotNull(chain, "Certificate chain should not be null");
        assertTrue(chain.length > 0, "Certificate chain should contain at least one certificate");
        System.out.println("Certificate Chain Length: " + chain.length);

        for (int i = 0; i < chain.length; i++) {
            X509Certificate cert = (X509Certificate) chain[i];
            System.out.println("Chain[" + i + "] Subject: " + cert.getSubjectX500Principal().getName());
        }
    }

    @Test
    @DisplayName("Should list all keystore aliases")
    void testListAliases() throws Exception {
        // Given
        File keystoreFile = new File(TEST_KEYSTORE_PATH);
        KeyStore keyStore = loadKeyStore(keystoreFile, TEST_KEYSTORE_PASSWORD);

        // When
        Enumeration<String> aliases = keyStore.aliases();

        // Then
        assertNotNull(aliases, "Aliases enumeration should not be null");
        assertTrue(aliases.hasMoreElements(), "KeyStore should contain at least one alias");

        System.out.println("Keystore Aliases:");
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            System.out.println("  - " + alias);
            assertTrue(keyStore.isKeyEntry(alias), "Alias should be a key entry");
        }
    }

    @Test
    @DisplayName("Should fail with incorrect password")
    void testLoadKeystoreWithWrongPassword() {
        // Given
        File keystoreFile = new File(TEST_KEYSTORE_PATH);
        String wrongPassword = "wrongpassword";

        // When & Then
        assertThrows(IOException.class, () -> {
            loadKeyStore(keystoreFile, wrongPassword);
        }, "Loading keystore with wrong password should throw IOException");
    }

    @Test
    @DisplayName("Should fail with non-existent keystore file")
    void testLoadNonExistentKeystore(@TempDir Path tempDir) {
        // Given
        File nonExistentFile = tempDir.resolve("non_existent.p12").toFile();

        // When & Then
        assertThrows(IOException.class, () -> {
            loadKeyStore(nonExistentFile, TEST_KEYSTORE_PASSWORD);
        }, "Loading non-existent keystore should throw IOException");
    }

    @Test
    @DisplayName("Should verify certificate validity period")
    void testCertificateValidity() throws Exception {
        // Given
        File keystoreFile = new File(TEST_KEYSTORE_PATH);
        KeyStore keyStore = loadKeyStore(keystoreFile, TEST_KEYSTORE_PASSWORD);
        X509Certificate cert = (X509Certificate) keyStore.getCertificate(TEST_KEY_ALIAS);

        // When & Then
        assertDoesNotThrow(() -> {
            cert.checkValidity();
        }, "Certificate should be valid for current date");

        assertNotNull(cert.getNotBefore(), "Certificate start date should not be null");
        assertNotNull(cert.getNotAfter(), "Certificate end date should not be null");
        assertTrue(cert.getNotBefore().before(cert.getNotAfter()),
                   "Certificate start date should be before end date");
    }

    /**
     * Helper method to load a KeyStore from a file.
     *
     * @param keystoreFile The keystore file
     * @param password The keystore password
     * @return Loaded KeyStore instance
     * @throws KeyStoreException If KeyStore cannot be initialized
     * @throws IOException If file cannot be read
     * @throws NoSuchAlgorithmException If algorithm is not available
     * @throws CertificateException If certificates cannot be loaded
     */
    private KeyStore loadKeyStore(File keystoreFile, String password)
            throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {

        if (!keystoreFile.exists() || !keystoreFile.isFile()) {
            throw new IOException("Keystore file does not exist: " + keystoreFile.getAbsolutePath());
        }

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(keystoreFile)) {
            keyStore.load(fis, password.toCharArray());
        }
        return keyStore;
    }
}
