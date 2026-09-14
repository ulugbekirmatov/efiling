package com.irs.mef.newsend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.irs.mef.newsend.journal.FileNewSendSubmissionJournal;
import com.irs.mef.newsend.journal.InMemoryNewSendSubmissionJournal;
import com.irs.mef.newsend.journal.NewSendSubmissionJournal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Bean wiring for the NewSend vertical. */
@Configuration
@Slf4j
public class NewSendConfig {

    @Bean
    public Clock newSendClock() {
        return Clock.systemUTC();
    }

    @Bean
    public NewSendSubmissionJournal newSendSubmissionJournal(NewSendProperties properties,
                                                             ObjectMapper objectMapper) {
        if (properties.getJournal().getMode() == NewSendProperties.Journal.Mode.MEMORY) {
            log.warn("NewSend journal is IN-MEMORY — filings will not survive a restart. Test use only.");
            return new InMemoryNewSendSubmissionJournal();
        }
        return new FileNewSendSubmissionJournal(properties.getJournal().resolvedPath(), objectMapper);
    }
}
