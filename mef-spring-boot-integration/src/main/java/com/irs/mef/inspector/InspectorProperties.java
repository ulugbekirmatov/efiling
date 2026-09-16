package com.irs.mef.inspector;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/** PII bound for the send inspector: last N local snapshots, gitignored path. */
@Configuration
@ConfigurationProperties(prefix = "mef.inspector")
@Data
public class InspectorProperties {

    private boolean enabled = true;

    /** Last-N ring size. Clamped to 1..100 at bind. */
    private int ringSize = 20;

    private String path = "data/inspector-ring";

    /** Skip storing a SOAP/Return document above this UTF-8 size. Never thrown into the IRS send. */
    private long maxXmlBytes = 1_048_576L;

    public Path resolvedPath() {
        return Path.of(path);
    }

    public int ringSizeClamped() {
        return Math.min(100, Math.max(1, ringSize));
    }

    public long maxXmlBytesOrDefault() {
        return maxXmlBytes < 1 ? 1_048_576L : maxXmlBytes;
    }

    public void setRingSize(int ringSize) {
        this.ringSize = Math.min(100, Math.max(1, ringSize));
    }
}
