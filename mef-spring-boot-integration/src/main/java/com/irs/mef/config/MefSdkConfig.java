package com.irs.mef.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds the MeF settings the process actually reads. Endpoint URLs and WS-Security live in the
 * SDK's own {@code mef_config} files, selected by {@code A2A_TOOLKIT_HOME}.
 */
@Configuration
@ConfigurationProperties(prefix = "mef.sdk")
@Data
public class MefSdkConfig {

    /** ATS (test) or PRD (production). */
    private String environment = "ATS";

    private Certificate certificate = new Certificate();

    private Authentication authentication = new Authentication();

    public boolean isCertificateConfigured() {
        return certificate != null
                && certificate.getKeystorePath() != null
                && !certificate.getKeystorePath().isEmpty()
                && certificate.getKeystorePassword() != null
                && !certificate.getKeystorePassword().isEmpty();
    }

    @Data
    public static class Certificate {
        private String keystorePath;
        private String keystorePassword;
        private String keyAlias;
    }

    @Data
    public static class Authentication {
        private String etin;
        private String asid;
    }
}
