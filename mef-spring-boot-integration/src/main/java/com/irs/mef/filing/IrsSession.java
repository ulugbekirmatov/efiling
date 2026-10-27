package com.irs.mef.filing;

import com.irs.mef.ack.StoredAck;
import com.irs.mef.config.MefSdkConfig;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.security.auth.x500.X500Principal;
import java.io.File;
import java.lang.reflect.Method;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Sole holder of the SDK ServiceContext. The context is not thread-safe, and a second one
 * invalidates the IRS session, so every SDK call takes the same fair lock.
 *
 * A failed call does not log in again: the expiry fault is not known, so a retry would guess.
 */
@Component
@Slf4j
public final class IrsSession {

    private final ReentrantLock lock = new ReentrantLock(true);
    private final SessionLogin login;
    private final MefSdkConfig config;
    private final IrsWire wire;
    private final int sessionWaitSeconds;

    private Object serviceContext;
    private String sessionId;

    IrsSession(SessionLogin login, MefSdkConfig config, IrsWire wire, FilingProperties properties) {
        this.login = login;
        this.config = config;
        this.wire = wire;
        this.sessionWaitSeconds = properties.getSessionWaitSeconds();
    }

    public SessionStatus status() {
        lock.lock();
        try {
            return currentStatus();
        } finally {
            lock.unlock();
        }
    }

    /** Log in now, replacing any session this process already holds. */
    public SessionStatus probe() {
        lock.lock();
        try {
            open();
            return currentStatus();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Ends the IRS session when there is one, then clears local state either way.
     * A stale local session would keep sending a SAML token the IRS no longer honours.
     */
    public void logout() {
        lock.lock();
        try {
            if (serviceContext == null) {
                return;
            }
            Object context = serviceContext;
            try {
                String statusText = invokeLogout(context);
                log.info("IRS session {} terminated: {}", sessionId, statusText);
            } catch (Exception e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                log.error("IRS logout failed for session {}; clearing the local session anyway: {}",
                        sessionId, cause.getMessage());
            } finally {
                serviceContext = null;
                sessionId = null;
            }
        } finally {
            lock.unlock();
        }
    }

    /** GetAck for one submission. The ServiceContext stays inside this package. */
    public Optional<StoredAck> getAck(String submissionId) {
        return call(ctx -> wire.getAck(ctx, submissionId));
    }

    /**
     * Runs {@code body} with the current context, logging in first when there is no session.
     * Refuses inside a GET request before taking the lock or logging in: a GET route cannot reach IRS.
     * Waits at most {@code mef.newsend.session-wait-seconds} for the lock, then throws before
     * touching the context.
     */
    <T> T call(IrsCall<T> body) {
        Objects.requireNonNull(body, "body");
        refuseGet();
        boolean locked;
        try {
            locked = lock.tryLock(sessionWaitSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FilingBusyException(sessionWaitSeconds);
        }
        if (!locked) {
            throw new FilingBusyException(sessionWaitSeconds);
        }
        try {
            if (serviceContext == null) {
                open();
            }
            return body.run(serviceContext);
        } finally {
            lock.unlock();
        }
    }

    /** Keystore and certificate details. Does not log in and does not take the session lock. */
    public CertificateDiagnostic certificate() {
        if (!config.isCertificateConfigured()) {
            return new CertificateDiagnostic(false, null, null, "Certificate not configured", List.of());
        }
        String path = config.getCertificate().getKeystorePath();
        try {
            File file = new File(path);
            if (!file.isFile()) {
                return new CertificateDiagnostic(false, path, null,
                        "Keystore file not found at: " + path, List.of());
            }
            KeyStore keyStore = loadKeyStore(file, config.getCertificate().getKeystorePassword());
            List<CertificateDiagnostic.CertificateInfo> certificates = readCertificates(keyStore);
            return new CertificateDiagnostic(true, path, keyStore.getType(),
                    "Found " + certificates.size() + " certificate(s) with private keys", certificates);
        } catch (Exception e) {
            log.warn("Certificate diagnostic failed: {}", e.toString());
            return new CertificateDiagnostic(false, path, null,
                    "Certificate diagnostic failed: " + e.getMessage(), List.of());
        }
    }

    @PreDestroy
    void shutdown() {
        logout();
    }

    private void open() {
        SessionLogin.Opened opened = login.open();
        if (opened == null || opened.serviceContext() == null) {
            throw new IrsLoginException("Login returned no session");
        }
        serviceContext = opened.serviceContext();
        sessionId = opened.sessionId();
    }

    private SessionStatus currentStatus() {
        if (serviceContext == null) {
            return new SessionStatus(false, null, "No active session");
        }
        return new SessionStatus(true, sessionId, "Active session");
    }

    private static void refuseGet() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servlet) || servlet.getRequest() == null) {
            return;
        }
        if ("GET".equalsIgnoreCase(servlet.getRequest().getMethod())) {
            throw new IllegalStateException("A GET request cannot reach IRS");
        }
    }

    private static String invokeLogout(Object serviceContext) throws Exception {
        Class<?> serviceContextClass = Class.forName("gov.irs.mef.services.ServiceContext");
        Class<?> logoutClientClass = Class.forName("gov.irs.mef.services.msi.LogoutClient");
        Object logoutClient = logoutClientClass.getDeclaredConstructor().newInstance();
        Method invoke = logoutClientClass.getMethod("invoke", serviceContextClass);
        Object result = invoke.invoke(logoutClient, serviceContext);
        if (result == null) {
            return "no LogoutResult returned";
        }
        return String.valueOf(result.getClass().getMethod("getStatusTxt").invoke(result));
    }

    private static KeyStore loadKeyStore(File file, String password) throws Exception {
        Class<?> keyStoreUtil = Class.forName("gov.irs.mef.services.util.KeyStoreUtil");
        Method load = keyStoreUtil.getMethod("loadKeyStore", File.class, char[].class);
        return (KeyStore) load.invoke(null, file, password.toCharArray());
    }

    @SuppressWarnings("unchecked")
    private static List<CertificateDiagnostic.CertificateInfo> readCertificates(KeyStore keyStore) throws Exception {
        Class<?> keyStoreUtil = Class.forName("gov.irs.mef.services.util.KeyStoreUtil");
        List<String> aliases = (List<String>) keyStoreUtil
                .getMethod("listPrivateKeyAliases", KeyStore.class)
                .invoke(null, keyStore);
        Map<String, X500Principal> keyInfo = (Map<String, X500Principal>) keyStoreUtil
                .getMethod("listKeyInfo", KeyStore.class)
                .invoke(null, keyStore);
        List<CertificateDiagnostic.CertificateInfo> certificates = new ArrayList<>();
        if (aliases == null) {
            return certificates;
        }
        for (String alias : aliases) {
            X500Principal principal = keyInfo != null ? keyInfo.get(alias) : null;
            if (principal == null) {
                continue;
            }
            String subjectDn = principal.getName();
            X509Certificate cert = (X509Certificate) keyStore.getCertificate(alias);
            String issuerDn = cert != null ? cert.getIssuerX500Principal().getName() : null;
            String serial = cert != null ? cert.getSerialNumber().toString() : null;
            Map<String, String> dn = parseDn(subjectDn);
            certificates.add(new CertificateDiagnostic.CertificateInfo(
                    alias, subjectDn, issuerDn, serial, dn.get("CN"), dn.get("O")));
        }
        return certificates;
    }

    private static Map<String, String> parseDn(String dn) {
        Map<String, String> components = new java.util.HashMap<>();
        if (dn == null || dn.isEmpty()) {
            return components;
        }
        String[] parts = dn.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
        for (String part : parts) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2) {
                components.put(kv[0].trim(), kv[1].trim());
            }
        }
        return components;
    }
}
