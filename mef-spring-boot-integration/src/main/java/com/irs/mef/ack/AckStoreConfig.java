package com.irs.mef.ack;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AckStoreConfig {

    @Bean
    public AckStore ackStore(AckStoreProperties properties, ObjectMapper objectMapper) {
        return new FileAckStore(properties.resolvedPath(), objectMapper);
    }
}
