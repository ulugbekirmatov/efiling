package com.irs.mef.ack;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
@ConfigurationProperties(prefix = "mef.ack-store")
@Data
public class AckStoreProperties {

    private String path = "data/ack-store";

    public Path resolvedPath() {
        return Path.of(path);
    }
}
