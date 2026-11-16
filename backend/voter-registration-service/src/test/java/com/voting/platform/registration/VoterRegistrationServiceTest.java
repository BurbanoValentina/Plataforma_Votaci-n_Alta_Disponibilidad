package com.voting.platform.registration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voting.platform.domain.model.EligibleVoterRecord;
import com.voting.platform.domain.port.EligibleVoterPublisher;
import com.voting.platform.registration.service.VoterRegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("VoterRegistrationService - Isolated Unit Tests (AAA Pattern with Test Double Stub)")
class VoterRegistrationServiceTest {

    /**
     * Test Double Stub verifying DIP contract without broker or heavy reflection.
     */
    private static class RecordingEligibleVoterPublisher implements EligibleVoterPublisher {
        private final List<EligibleVoterRecord> published = new ArrayList<>();

        @Override
        public void publishEligibleVoter(EligibleVoterRecord eligibleVoter) {
            published.add(eligibleVoter);
        }

        public List<EligibleVoterRecord> getPublished() {
            return published;
        }

        public int count() {
            return published.size();
        }
    }

    private RecordingEligibleVoterPublisher publisher;
    private VoterRegistrationService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        publisher = new RecordingEligibleVoterPublisher();
        objectMapper = new ObjectMapper();
        service = new VoterRegistrationService(publisher, objectMapper);
    }

    @Test
    @DisplayName("Arrange valid voters JSON -> Act register -> Assert publishes all records with DIP")
    void shouldRegisterValidVoters() {
        // Arrange
        String json = """
            [
              { "voterId": "voter_001", "fullName": "Carlos Mendoza", "region": "NORTE" },
              { "voterId": "voter_002", "fullName": "Maria Fernandez", "region": "SUR" }
            ]
            """;
        InputStream is = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

        // Act
        int registered = service.registerVotersFromStream(is);

        // Assert
        assertThat(registered).isEqualTo(2);
        assertThat(publisher.count()).isEqualTo(2);

        EligibleVoterRecord first = publisher.getPublished().get(0);
        assertThat(first.voterId().value()).isEqualTo("voter_001");
        assertThat(first.fullName()).isEqualTo("Carlos Mendoza");

        EligibleVoterRecord second = publisher.getPublished().get(1);
        assertThat(second.voterId().value()).isEqualTo("voter_002");
        assertThat(second.fullName()).isEqualTo("Maria Fernandez");
    }

    @Test
    @DisplayName("Arrange JSON with invalid records -> Act register -> Assert skips malformed records gracefully")
    void shouldSkipMalformedVoters() {
        // Arrange: one valid, one with missing region, one with invalid voterId syntax
        String json = """
            [
              { "voterId": "voter_001", "fullName": "Carlos Mendoza", "region": "NORTE" },
              { "voterId": "voter_002", "fullName": "Missing Region" },
              { "voterId": "x", "fullName": "Invalid Short Voter Id", "region": "SUR" }
            ]
            """;
        InputStream is = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

        // Act
        int registered = service.registerVotersFromStream(is);

        // Assert: only voter_001 is accepted; malformed records are rejected Fail-Fast
        assertThat(registered).isEqualTo(1);
        assertThat(publisher.count()).isEqualTo(1);
        assertThat(publisher.getPublished().get(0).voterId().value()).isEqualTo("voter_001");
    }
}
