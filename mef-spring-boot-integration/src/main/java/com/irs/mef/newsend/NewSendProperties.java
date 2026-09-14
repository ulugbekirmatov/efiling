package com.irs.mef.newsend;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.time.ZoneId;

/** Configuration for the NewSend vertical. Binds {@code mef.newsend.*}; every field has a safe default. */
@Configuration
@ConfigurationProperties(prefix = "mef.newsend")
@Data
public class NewSendProperties {

    /**
     * Zone used for the submission id's yyyyDDD processing date. IRS day boundaries are an
     * IRS-side concept, so this is pinned to Eastern rather than the server's arbitrary default.
     */
    private String processingZone = "America/New_York";

    /** How long a submit waits for the single IRS session before returning 503. */
    private int sessionWaitSeconds = 30;

    /** Return XML size cap, in characters. */
    private long maxReturnXmlChars = 8_000_000L;

    /** ENFORCE (default): schema errors are a 400. WARN: logged, filing proceeds. OFF: well-formedness only. */
    private XmlValidationMode xmlValidation = XmlValidationMode.ENFORCE;

    private Journal journal = new Journal();

    public ZoneId processingZoneId() {
        return ZoneId.of(processingZone);
    }

    public enum XmlValidationMode { ENFORCE, WARN, OFF }

    @Data
    public static class Journal {

        /** FILE is the default — an in-memory write-ahead log cannot satisfy its own purpose. */
        private Mode mode = Mode.FILE;

        private String path = "data/newsend-journal.jsonl";

        public Path resolvedPath() {
            return Path.of(path);
        }

        public enum Mode { FILE, MEMORY }
    }
}
