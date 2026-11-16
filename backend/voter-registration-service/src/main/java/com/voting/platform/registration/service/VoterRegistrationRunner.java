package com.voting.platform.registration.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Objects;

/**
 * Bootstrap runner that automatically triggers voter registration on application startup.
 */
@Component
public class VoterRegistrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(VoterRegistrationRunner.class);

    private final VoterRegistrationService registrationService;
    private final ResourceLoader resourceLoader;
    private final String votersFilePath;

    public VoterRegistrationRunner(
        VoterRegistrationService registrationService,
        ResourceLoader resourceLoader,
        @Value("${voting.registration.file:classpath:voters.json}") String votersFilePath
    ) {
        this.registrationService = Objects.requireNonNull(registrationService, "registrationService cannot be null");
        this.resourceLoader = Objects.requireNonNull(resourceLoader, "resourceLoader cannot be null");
        this.votersFilePath = votersFilePath;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("Starting initial voter registration from path: {}", votersFilePath);
        Resource resource = resourceLoader.getResource(votersFilePath);

        if (!resource.exists()) {
            log.warn("Voters resource not found at {}. Checking fallback classpath:voters.json", votersFilePath);
            resource = resourceLoader.getResource("classpath:voters.json");
        }

        if (resource.exists()) {
            try (InputStream is = resource.getInputStream()) {
                int count = registrationService.registerVotersFromStream(is);
                log.info("Successfully bootstrapped {} voters into eligible_voters KTable topic.", count);
            }
        } else {
            log.error("Could not find voters.json file for registration bootstrap!");
        }
    }
}
