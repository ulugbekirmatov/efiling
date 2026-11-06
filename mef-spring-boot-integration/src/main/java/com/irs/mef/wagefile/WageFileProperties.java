package com.irs.mef.wagefile;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/** Binds {@code mef.wagefile.*}. */
@Configuration
@ConfigurationProperties(prefix = "mef.wagefile")
@Data
public class WageFileProperties {

    private String path = "data/wage-files";

    public Path resolvedPath() {
        return Path.of(path);
    }
}
