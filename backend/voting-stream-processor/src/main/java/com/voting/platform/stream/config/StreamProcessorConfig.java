package com.voting.platform.stream.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration class for the voting-stream-processor module.
 * Declares infrastructure beans such as the shared Jackson {@link ObjectMapper}.
 */
@Configuration
public class StreamProcessorConfig {

    /**
     * Creates and configures a shared {@link ObjectMapper} bean with registered
     * standard modules (e.g., Java 8 Date/Time module).
     *
     * @return a configured {@link ObjectMapper} instance
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
