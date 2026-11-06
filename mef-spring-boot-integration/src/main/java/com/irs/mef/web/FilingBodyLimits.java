package com.irs.mef.web;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.irs.mef.filing.ClientFiling;
import jakarta.servlet.Filter;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** JSON body caps. Bean validation runs only after Jackson has already allocated the string. */
@Configuration
class FilingBodyLimits {

    @Bean
    Jackson2ObjectMapperBuilderCustomizer clientBodyStringCap() {
        int cap = ClientFiling.MAX_CLIENT_BODY_CHARS;
        return builder -> builder.postConfigurer(mapper -> mapper.getFactory().setStreamReadConstraints(
                StreamReadConstraints.builder().maxStringLength(cap).build()));
    }

    @Bean
    Filter filingContentLengthFilter() {
        return new FilingContentLengthFilter();
    }
}
