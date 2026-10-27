package com.irs.mef.filing;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Bean wiring for the NewSend vertical. */
@Configuration
@Slf4j
class FilingConfig {

    @Bean
    Clock newSendClock() {
        return Clock.systemUTC();
    }

    @Bean
    Journal newSendSubmissionJournal(FilingProperties properties,
                                                             ObjectMapper objectMapper) {
        if (properties.getJournal().getMode() == FilingProperties.Journal.Mode.MEMORY) {
            log.warn("NewSend journal is IN-MEMORY — filings will not survive a restart. Test use only.");
            return new InMemoryJournal();
        }
        return new FileJournal(properties.getJournal().resolvedPath(), objectMapper);
    }
}
