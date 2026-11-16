package com.voting.platform.registration.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voting.platform.domain.model.EligibleVoterRecord;
import com.voting.platform.domain.model.Region;
import com.voting.platform.domain.model.VoterId;
import com.voting.platform.domain.port.EligibleVoterPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Service responsible for parsing, validating, and publishing eligible voters
 * into Kafka's compacted topic eligible_voters.
 * Follows Dependency Inversion Principle (DIP) with strict constructor injection.
 */
@Service
public class VoterRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(VoterRegistrationService.class);

    private final EligibleVoterPublisher publisher;
    private final ObjectMapper objectMapper;

    public VoterRegistrationService(EligibleVoterPublisher publisher, ObjectMapper objectMapper) {
        this.publisher = Objects.requireNonNull(publisher, "publisher cannot be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
    }

    /**
     * Reads a JSON stream of voter objects, maps to immutable EligibleVoterRecord,
     * and publishes each record to Kafka.
     *
     * @param inputStream JSON data stream
     * @return count of successfully registered voters
     */
    public int registerVotersFromStream(InputStream inputStream) {
        Objects.requireNonNull(inputStream, "inputStream cannot be null");
        try {
            List<Map<String, String>> rawList = objectMapper.readValue(
                inputStream,
                new TypeReference<List<Map<String, String>>>() {}
            );

            int registeredCount = 0;
            for (Map<String, String> item : rawList) {
                String voterIdStr = item.get("voterId");
                String fullName = item.get("fullName");
                String regionStr = item.get("region");

                if (voterIdStr == null || fullName == null || regionStr == null) {
                    log.warn("Skipping malformed voter record missing required fields: {}", item);
                    continue;
                }

                try {
                    VoterId voterId = VoterId.of(voterIdStr);
                    Region region = Region.fromString(regionStr);
                    EligibleVoterRecord record = new EligibleVoterRecord(voterId, fullName, region, Instant.now());

                    publisher.publishEligibleVoter(record);
                    registeredCount++;
                    log.info("Published eligible voter: id={}, name={}, region={}", voterId, fullName, region);
                } catch (IllegalArgumentException e) {
                    log.error("Invalid voter data syntax for record {}: {}", item, e.getMessage());
                }
            }

            log.info("Voter registration completed. Total eligible voters registered: {}", registeredCount);
            return registeredCount;
        } catch (Exception e) {
            log.error("Failed to parse voter registration input stream: {}", e.getMessage(), e);
            throw new RuntimeException("Error processing voters input: " + e.getMessage(), e);
        }
    }
}
