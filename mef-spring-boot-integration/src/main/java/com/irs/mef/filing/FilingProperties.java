package com.irs.mef.filing;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/** Configuration for the filing desk. Binds {@code mef.newsend.*}; every field has a safe default. */
@Configuration
@ConfigurationProperties(prefix = "mef.newsend")
@Data
class FilingProperties {

    /** How long a submit waits for the single IRS session before returning 503. */
    private int sessionWaitSeconds = 30;

    /** Return XML size cap, in characters. */
    private long maxReturnXmlChars = 8_000_000L;

    private Journal journal = new Journal();

    @Data
    static class Journal {

        /** FILE is the default — an in-memory write-ahead log cannot satisfy its own purpose. */
        private Mode mode = Mode.FILE;

        private String path = "data/newsend-journal.jsonl";

        Path resolvedPath() {
            return Path.of(path);
        }

        enum Mode { FILE, MEMORY }
    }
}
