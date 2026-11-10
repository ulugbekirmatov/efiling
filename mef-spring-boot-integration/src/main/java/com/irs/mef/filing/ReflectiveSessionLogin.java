package com.irs.mef.filing;

import com.irs.mef.config.MefSdkConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;

import java.io.File;
import java.lang.reflect.Method;

@Component
@Slf4j
final class ReflectiveSessionLogin implements SessionLogin {

    private final MefSdkConfig config;

    ReflectiveSessionLogin(MefSdkConfig config) {
        this.config = config;
    }

    @Override
    public Opened open() {
        String etin = config.getAuthentication() != null ? config.getAuthentication().getEtin() : null;
        log.info("Attempting login for ETIN: {}", etin);
        try {
            Class<?> etinClass = Class.forName("gov.irs.mef.services.data.ETIN");
            Class<?> testCdTypeClass = Class.forName("gov.irs.a2a.mef.mefheader.TestCdType");
            Class<?> serviceContextClass = Class.forName("gov.irs.mef.services.ServiceContext");
            Class<?> loginClientClass = Class.forName("gov.irs.mef.services.msi.LoginClient");

            Object etinObj = null;
            if (etin != null && !etin.isEmpty()) {
                etinObj = etinClass.getConstructor(String.class).newInstance(etin);
            }

            boolean production = "PRD".equalsIgnoreCase(config.getEnvironment());
            Object testMode = production
                    ? enumValue(testCdTypeClass, "P")
                    : enumValue(testCdTypeClass, "T");

            String asid = config.getAuthentication() != null ? config.getAuthentication().getAsid() : null;
            if (asid == null || asid.isBlank()) {
                log.warn("ASID not configured - authentication may fail");
            }

            Object serviceContext = serviceContextClass
                    .getConstructor(etinClass, String.class, testCdTypeClass)
                    .newInstance(etinObj, asid, testMode);

            File keystoreFile = null;
            String keystorePassword = null;
            String keyAlias = null;
            if (config.isCertificateConfigured()) {
                String keystorePath = config.getCertificate().getKeystorePath();
                keystoreFile = new File(keystorePath);
                if (!keystoreFile.isFile()) {
                    throw new IrsLoginException("Keystore file does not exist at: " + keystorePath);
                }
                keystorePassword = config.getCertificate().getKeystorePassword();
                keyAlias = config.getCertificate().getKeyAlias();
                log.info("Using keystore: {} alias: {}", keystorePath, keyAlias);
            } else {
                log.warn("Certificate not configured - authentication may fail");
            }

            Object loginClient = loginClientClass.getDeclaredConstructor().newInstance();
            Method invoke = loginClientClass.getMethod("invoke",
                    serviceContextClass, File.class, String.class, String.class);
            invoke.invoke(loginClient, serviceContext, keystoreFile, keystorePassword, keyAlias);

            Object sessionInfo = serviceContextClass.getMethod("getSessionInfo").invoke(serviceContext);
            if (sessionInfo == null) {
                throw new IrsLoginException("Login failed - no session info returned");
            }
            Class<?> sessionInfoClass = Class.forName("gov.irs.mef.services.SessionInfo");
            Element saml = (Element) sessionInfoClass.getMethod("getSAMLToken").invoke(sessionInfo);
            if (saml == null) {
                throw new IrsLoginException("Login failed - no SAML token returned");
            }
            String sessionId = assertionId(saml);
            if (sessionId == null) {
                sessionId = "SESSION_" + System.currentTimeMillis();
            }
            log.info("Login successful for ETIN: {} session: {}", etin, sessionId);
            return new Opened(serviceContext, sessionId);
        } catch (IrsLoginException e) {
            throw e;
        } catch (Exception e) {
            String message = deepestMessage(e);
            log.error("Login failed for ETIN: {}: {}", etin, message);
            throw new IrsLoginException(message, e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValue(Class<?> enumClass, String name) {
        return Enum.valueOf((Class<Enum>) enumClass, name);
    }

    private static String assertionId(Element saml) {
        String id = saml.getAttribute("ID");
        if (id != null && !id.isEmpty()) {
            return id;
        }
        id = saml.getAttribute("AssertionID");
        if (id != null && !id.isEmpty()) {
            return id;
        }
        return null;
    }

    private static String deepestMessage(Throwable error) {
        String message = error.getMessage();
        Throwable cause = error;
        while (cause != null) {
            if (cause.getMessage() != null) {
                message = cause.getMessage();
            }
            cause = cause.getCause();
        }
        return message != null ? message : error.toString();
    }
}
