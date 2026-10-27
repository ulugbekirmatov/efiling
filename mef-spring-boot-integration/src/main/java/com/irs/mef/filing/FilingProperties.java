package com.irs.mef.filing;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
@ConfigurationProperties(prefix = "mef.newsend")
@Data
class FilingProperties {

    private int sessionWaitSeconds = 30;

    private long maxReturnXmlChars = ClientFiling.MAX_CLIENT_BODY_CHARS;

    private Journal journal = new Journal();

    @Data
    static class Journal {

        private Mode mode = Mode.FILE;

        private String path = "data/newsend-journal.jsonl";

        Path resolvedPath() {
            return Path.of(path);
        }

        enum Mode { FILE, MEMORY }
    }
}
