package com.voting.platform.stream;

import com.voting.platform.domain.aggregate.BotDetectorAggregate;
import com.voting.platform.domain.model.ElectionType;
import com.voting.platform.domain.model.EligibleVoterRecord;
import com.voting.platform.domain.model.GlobalResultRecord;
import com.voting.platform.domain.model.Region;
import com.voting.platform.domain.model.RegionalResultRecord;
import com.voting.platform.domain.model.SecurityAlertRecord;
import com.voting.platform.domain.model.VoteRecord;
import com.voting.platform.domain.model.VoteSource;
import com.voting.platform.domain.model.VoterId;
import com.voting.platform.domain.port.EligibleVoterPublisher;
import com.voting.platform.domain.port.GlobalResultPublisher;
import com.voting.platform.domain.port.RegionalResultPublisher;
import com.voting.platform.domain.port.SecurityAlertPublisher;
import com.voting.platform.domain.port.SecurityIncidentRepositoryPort;
import com.voting.platform.domain.port.VoteAuditRepositoryPort;
import com.voting.platform.domain.strategy.UniformWeightingStrategy;
import com.voting.platform.stream.service.AnalyticsArchiverService;
import com.voting.platform.stream.service.BotDetectorService;
import com.voting.platform.stream.service.RegionalRollupService;
import com.voting.platform.stream.service.VoteProcessorService;
import com.voting.platform.stream.service.VoterRegistrationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Isolated TDD Unit Tests for all Stream Processor services (JUnit 5 + Mockito, AAA Pattern).
 * Executes purely in RAM without Kafka, RabbitMQ, or PostgreSQL.
 */
@ExtendWith(MockitoExtension.class)
class StreamServicesUnitTest {

    @Mock
    private GlobalResultPublisher globalResultPublisher;
    @Mock
    private RegionalResultPublisher regionalResultPublisher;
    @Mock
    private SecurityAlertPublisher securityAlertPublisher;
    @Mock
    private SecurityIncidentRepositoryPort securityIncidentRepository;
    @Mock
    private VoteAuditRepositoryPort voteAuditRepository;
    @Mock
    private EligibleVoterPublisher eligibleVoterPublisher;

    @Test
    @DisplayName("AAA: VoteProcessorService separates REAL vs SIMULATED and NACIONAL vs REGIONAL")
    void shouldSeparateRealSimulatedAndElectionTypes() {
        // Arrange
        VoteProcessorService processor = new VoteProcessorService(globalResultPublisher);
        VoteRecord realNac = VoteRecord.create(
            "1001234567", "cand_nac_01", "Bogotá", "CENTRO", "NACIONAL", "REAL", "10.1.1.1", "TKT-1", "node-1", Instant.now()
        );
        VoteRecord simNac = VoteRecord.create(
            "SIM-1001", "cand_nac_01", "Cali", "OESTE", "NACIONAL", "SIMULATED", "10.1.1.2", "TKT-2", "node-2", Instant.now()
        );
        VoteRecord realReg = VoteRecord.create(
            "1001234567", "cand_reg_02", "Bogotá", "CENTRO", "REGIONAL", "REAL", "10.1.1.1", "TKT-3", "node-1", Instant.now()
        );

        // Act
        processor.processVote(realNac);
        processor.processVote(simNac);
        processor.processVote(realReg);
        GlobalResultRecord snapshot = processor.publishGlobalSnapshot();

        // Assert
        assertThat(snapshot.totalVotes()).isEqualTo(3L);
        assertThat(snapshot.totalRealVotes()).isEqualTo(2L);
        assertThat(snapshot.totalSimulatedVotes()).isEqualTo(1L);
        assertThat(snapshot.totalNationalVotes()).isEqualTo(2L);
        assertThat(snapshot.totalRegionalVotes()).isEqualTo(1L);
        assertThat(snapshot.nationalRealVotes().get("cand_nac_01")).isEqualTo(1L);
        assertThat(snapshot.nationalSimulatedVotes().get("cand_nac_01")).isEqualTo(1L);
        verify(globalResultPublisher).publishGlobalResults(snapshot);
    }

    @Test
    @DisplayName("AAA: RegionalRollupService applies GoF Strategy Pattern and swaps algorithms cleanly")
    void shouldApplyAndSwapRegionalWeightingStrategies() {
        // Arrange
        RegionalRollupService rollup = new RegionalRollupService(regionalResultPublisher);
        VoteRecord v1 = VoteRecord.create(
            "1001", "cand_reg_01", "Barranquilla", "NORTE", "REGIONAL", "REAL", "10.0.0.1", "TKT-1", "node-1", Instant.now()
        );
        VoteRecord v2 = VoteRecord.create(
            "SIM-2", "cand_reg_01", "Cartagena", "NORTE", "REGIONAL", "SIMULATED", "10.0.0.2", "TKT-2", "node-2", Instant.now()
        );

        // Act
        rollup.processRegionalVote(v1);
        rollup.processRegionalVote(v2);
        RegionalResultRecord weighted = rollup.snapshotFor(Region.NORTE);
        rollup.changeWeightingStrategy(new UniformWeightingStrategy());
        RegionalResultRecord uniform = rollup.snapshotFor(Region.NORTE);
        rollup.publishAllRegionalSnapshots();

        // Assert
        assertThat(weighted.totalRegionalVotes()).isEqualTo(2L);
        assertThat(weighted.totalRealVotes()).isEqualTo(1L);
        assertThat(weighted.totalSimulatedVotes()).isEqualTo(1L);
        assertThat(weighted.weightedVotes().get("cand_reg_01")).isEqualTo(2.5); // 2 * 1.25 (NORTE)
        assertThat(uniform.weightedVotes().get("cand_reg_01")).isEqualTo(2.0); // Uniform 1.0x
        verify(regionalResultPublisher).publishRegionalResults(eq("results.norte"), any(RegionalResultRecord.class));
        verify(regionalResultPublisher, times(Region.values().length)).publishRegionalResults(any(), any());
    }

    @Test
    @DisplayName("AAA: BotDetectorService triggers GoF State transitions and persists forensic alerts")
    void shouldDetectBotAttackAndPersistIncident() {
        // Arrange
        BotDetectorAggregate aggregate = new BotDetectorAggregate(Duration.ofSeconds(10), 3, 6);
        BotDetectorService botService = new BotDetectorService(aggregate, securityAlertPublisher, securityIncidentRepository);
        Instant base = Instant.now();

        // Act
        Optional<SecurityAlertRecord> alertAt3 = Optional.empty();
        for (int i = 1; i <= 6; i++) {
            VoteRecord v = VoteRecord.of("bot_" + i, "cand_nac_01", "CENTRO", "185.220.101.47", base.plusMillis(i * 100L));
            Optional<SecurityAlertRecord> res = botService.analyzeVote(v);
            if (i == 3) alertAt3 = res;
        }

        // Assert
        assertThat(alertAt3).isPresent();
        assertThat(alertAt3.get().riskLevel()).isEqualTo("SOSPECHOSA");
        verify(securityAlertPublisher, atLeastOnce()).publishSecurityAlert(any(SecurityAlertRecord.class));
        verify(securityIncidentRepository, atLeastOnce()).saveIncident(any(SecurityAlertRecord.class));
    }

    @Test
    @DisplayName("AAA: AnalyticsArchiverService delegates vote persistence to DIP port")
    void shouldArchiveVoteViaPort() {
        // Arrange
        AnalyticsArchiverService archiver = new AnalyticsArchiverService(voteAuditRepository);
        VoteRecord vote = VoteRecord.of("1001234567", "cand_nac_02", "SUR", "192.168.1.5", Instant.now());

        // Act
        archiver.archiveVote(vote);

        // Assert
        verify(voteAuditRepository).saveAudit(vote);
    }

    @Test
    @DisplayName("AAA: VoterRegistrationService publishes eligible batch to Kafka KTable port")
    void shouldRegisterEligibleBatch() {
        // Arrange
        VoterRegistrationService regService = new VoterRegistrationService(eligibleVoterPublisher);
        List<EligibleVoterRecord> batch = List.of(
            new EligibleVoterRecord(VoterId.of("1001234567"), "Carlos", Region.NORTE, Instant.now()),
            new EligibleVoterRecord(VoterId.of("1002345678"), "Maria", Region.SUR, Instant.now())
        );

        // Act
        int count = regService.registerBatch(batch);

        // Assert
        assertThat(count).isEqualTo(2);
        verify(eligibleVoterPublisher, times(2)).publishEligibleVoter(any(EligibleVoterRecord.class));
    }
}
