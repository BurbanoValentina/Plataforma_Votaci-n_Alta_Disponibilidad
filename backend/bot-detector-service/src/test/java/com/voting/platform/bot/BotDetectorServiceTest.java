package com.voting.platform.bot;

import com.voting.platform.bot.service.BotDetectorService;
import com.voting.platform.domain.aggregate.BotDetectorAggregate;
import com.voting.platform.domain.model.CandidateId;
import com.voting.platform.domain.model.IpAddress;
import com.voting.platform.domain.model.Region;
import com.voting.platform.domain.model.SecurityAlertRecord;
import com.voting.platform.domain.model.VoteRecord;
import com.voting.platform.domain.model.VoterId;
import com.voting.platform.domain.port.SecurityAlertPublisher;
import com.voting.platform.domain.port.SecurityIncidentRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BotDetectorService - Anti-Fraud & Forensic Audit Unit Tests (AAA Pattern)")
class BotDetectorServiceTest {

    private static class StubSecurityAlertPublisher implements SecurityAlertPublisher {
        private final List<SecurityAlertRecord> alerts = new ArrayList<>();

        @Override
        public void publishSecurityAlert(SecurityAlertRecord alert) {
            alerts.add(alert);
        }

        public List<SecurityAlertRecord> getAlerts() {
            return alerts;
        }

        public int count() {
            return alerts.size();
        }
    }

    private static class StubSecurityIncidentRepository implements SecurityIncidentRepositoryPort {
        private final List<SecurityAlertRecord> incidents = new ArrayList<>();

        @Override
        public void saveIncident(SecurityAlertRecord alert) {
            incidents.add(alert);
        }

        @Override
        public long count() {
            return incidents.size();
        }

        public List<SecurityAlertRecord> getIncidents() {
            return incidents;
        }
    }

    private BotDetectorAggregate detectorAggregate;
    private StubSecurityAlertPublisher alertPublisher;
    private StubSecurityIncidentRepository incidentRepository;
    private BotDetectorService service;

    @BeforeEach
    void setUp() {
        // Warning at 3 votes within 5 seconds, block at 6
        detectorAggregate = new BotDetectorAggregate(Duration.ofSeconds(5), 3, 6);
        alertPublisher = new StubSecurityAlertPublisher();
        incidentRepository = new StubSecurityIncidentRepository();
        service = new BotDetectorService(detectorAggregate, alertPublisher, incidentRepository);
    }

    @Test
    @DisplayName("Arrange normal rate votes -> Act inspectVote -> Assert no alert published and no DB incident saved")
    void shouldNotAlertOnNormalVotingRate() {
        // Arrange
        IpAddress ip = IpAddress.of("192.168.1.55");
        VoteRecord vote1 = new VoteRecord(VoterId.of("voter_001"), CandidateId.of("cand_A"), Region.NORTE, ip, Instant.now());
        VoteRecord vote2 = new VoteRecord(VoterId.of("voter_002"), CandidateId.of("cand_A"), Region.NORTE, ip, Instant.now());

        // Act
        Optional<SecurityAlertRecord> r1 = service.inspectVote(vote1);
        Optional<SecurityAlertRecord> r2 = service.inspectVote(vote2);

        // Assert
        assertThat(r1).isEmpty();
        assertThat(r2).isEmpty();
        assertThat(alertPublisher.count()).isEqualTo(0);
        assertThat(incidentRepository.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("Arrange anomaly burst -> Act inspectVote -> Assert State Pattern transitions, Kafka alert published and PostgreSQL incident saved")
    void shouldTriggerAlertAndSaveIncidentOnRateSpike() {
        // Arrange: 3 votes in quick succession from same IP
        IpAddress ip = IpAddress.of("192.168.1.99");
        Instant now = Instant.now();

        VoteRecord v1 = new VoteRecord(VoterId.of("voter_001"), CandidateId.of("cand_A"), Region.NORTE, ip, now);
        VoteRecord v2 = new VoteRecord(VoterId.of("voter_002"), CandidateId.of("cand_A"), Region.NORTE, ip, now.plusMillis(50));
        VoteRecord v3 = new VoteRecord(VoterId.of("voter_003"), CandidateId.of("cand_A"), Region.NORTE, ip, now.plusMillis(100));

        // Act
        service.inspectVote(v1);
        service.inspectVote(v2);
        Optional<SecurityAlertRecord> alertOpt = service.inspectVote(v3);

        // Assert: 3rd vote crossed warning threshold 3
        assertThat(alertOpt).isPresent();
        SecurityAlertRecord alert = alertOpt.get();
        assertThat(alert.ipAddress()).isEqualTo(ip);
        assertThat(alert.riskLevel()).isEqualTo("SOSPECHOSA");
        assertThat(alert.voteCountInWindow()).isEqualTo(3);

        // Verify Kafka output: published alert to security_alerts
        assertThat(alertPublisher.count()).isEqualTo(1);
        assertThat(alertPublisher.getAlerts().get(0).riskLevel()).isEqualTo("SOSPECHOSA");

        // Verify PostgreSQL output: incident persisted for forensic audit
        assertThat(incidentRepository.count()).isEqualTo(1);
        assertThat(incidentRepository.getIncidents().get(0).ipAddress()).isEqualTo(ip);
    }
}
