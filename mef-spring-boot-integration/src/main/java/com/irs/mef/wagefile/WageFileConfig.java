package com.irs.mef.wagefile;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WageFileConfig {

    @Bean
    public WageFileStore wageFileStore(WageFileProperties properties, ObjectMapper objectMapper) {
        return new FileWageFileStore(properties.resolvedPath(), objectMapper);
    }
}
