package com.voting.platform.edge;

import com.voting.platform.domain.exception.DuplicateVoteException;
import com.voting.platform.domain.exception.SecurityThresholdExceededException;
import com.voting.platform.domain.exception.VoterNotEligibleException;
import com.voting.platform.domain.exception.VotingClosedException;
import com.voting.platform.domain.exception.VotingInAuditException;
import com.voting.platform.domain.model.ElectionType;
import com.voting.platform.domain.model.IpAddress;
import com.voting.platform.domain.model.Region;
import com.voting.platform.domain.model.SecurityAlertRecord;
import com.voting.platform.domain.model.VoteRecord;
import com.voting.platform.domain.model.VoteRequestRecord;
import com.voting.platform.domain.model.VoteSource;
import com.voting.platform.domain.model.VoteTicketRecord;
import com.voting.platform.domain.model.VoterId;
import com.voting.platform.domain.port.ElectionStateRepositoryPort;
import com.voting.platform.domain.port.EligibleVoterPublisher;
import com.voting.platform.domain.port.FraudGuardPort;
import com.voting.platform.domain.port.NotificationPort;
import com.voting.platform.domain.port.SecurityAlertPublisher;
import com.voting.platform.domain.port.VoteEventPublisher;
import com.voting.platform.domain.port.VoteTicketRepositoryPort;
import com.voting.platform.domain.port.VoterEligibilityStore;
import com.voting.platform.edge.service.VotingGatewayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Isolated TDD Unit Tests (JUnit 5 + Mockito, AAA Pattern, in-memory RAM).
 * Zero external Kafka, RabbitMQ, or PostgreSQL instances required.
 */
@ExtendWith(MockitoExtension.class)
class VotingGatewayServiceTest {

    @Mock
    private VoterEligibilityStore eligibilityStore;
    @Mock
    private EligibleVoterPublisher eligibleVoterPublisher;
    @Mock
    private VoteEventPublisher votePublisher;
    @Mock
    private VoteTicketRepositoryPort ticketRepository;
    @Mock
    private ElectionStateRepositoryPort stateRepository;
    @Mock
    private FraudGuardPort fraudGuard;
    @Mock
    private SecurityAlertPublisher securityAlertPublisher;
    @Mock
    private NotificationPort notificationPort;

    private VotingGatewayService votingService;

    @BeforeEach
    void setUp() {
        votingService = new VotingGatewayService(
            eligibilityStore,
            eligibleVoterPublisher,
            votePublisher,
            ticketRepository,
            stateRepository,
            fraudGuard,
            securityAlertPublisher,
            notificationPort,
            null,
            "aws-node-1-us-east-1a"
        );
    }

    @Test
    @DisplayName("AAA: Valid citizen vote generates official ticket, publishes to Kafka and notifies Telegram")
    void shouldAcceptValidCitizenVoteAndReturnTicket() {
        // Arrange
        VoteRequestRecord req = new VoteRequestRecord(
            "1085321456", "Valentina Burbano", "cand_nac_01", "Bogotá", null, "NACIONAL", "190.14.22.10", "REAL"
        );
        when(stateRepository.getLifecycleStateName()).thenReturn("HABILITADO");
        when(fraudGuard.getBlockReason("190.14.22.10", "1085321456")).thenReturn(Optional.empty());
        when(stateRepository.isStrictCensusMode()).thenReturn(false);
        when(ticketRepository.findByVoterAndElection(VoterId.of("1085321456"), ElectionType.NACIONAL))
            .thenReturn(Optional.empty());
        when(eligibilityStore.isEligible(VoterId.of("1085321456"))).thenReturn(true);
        when(ticketRepository.saveIfAbsent(any(VoteTicketRecord.class))).thenReturn(true);

        // Act
        VoteTicketRecord ticket = votingService.castVote(req, "190.14.22.10");

        // Assert
        assertThat(ticket.ticketCode()).startsWith("TKT-NAC-");
        assertThat(ticket.voterId()).isEqualTo("1085321456");
        assertThat(ticket.region()).isEqualTo(Region.CENTRO);
        assertThat(ticket.voteSource()).isEqualTo(VoteSource.REAL);
        ArgumentCaptor<VoteRecord> captor = ArgumentCaptor.forClass(VoteRecord.class);
        verify(votePublisher).publishRawVote(captor.capture());
        verify(notificationPort).notifyRealVote(ticket);
        assertThat(captor.getValue().kafkaKey()).isEqualTo("1085321456:NACIONAL");
    }

    @Test
    @DisplayName("AAA: Duplicate cedula in same election throws DuplicateVoteException carrying original ticket")
    void shouldRejectDuplicateCedulaAndReturnOriginalTicket() {
        // Arrange
        VoteTicketRecord existing = new VoteTicketRecord(
            "TKT-NAC-ABCD123456", "1085321456", "Valentina Burbano", "Bogotá",
            Region.CENTRO, ElectionType.NACIONAL, "cand_nac_01", "Elena Vargas",
            VoteSource.REAL, "aws-node-2-us-east-1b", "AABBCCDD11223344", Instant.now()
        );
        VoteRequestRecord req = new VoteRequestRecord(
            "1085321456", "Valentina Burbano", "cand_nac_02", "Bogotá", null, "NACIONAL", "190.14.22.10", "REAL"
        );
        when(stateRepository.getLifecycleStateName()).thenReturn("HABILITADO");
        when(fraudGuard.getBlockReason(anyString(), anyString())).thenReturn(Optional.empty());
        when(ticketRepository.findByVoterAndElection(VoterId.of("1085321456"), ElectionType.NACIONAL))
            .thenReturn(Optional.of(existing));
        when(fraudGuard.recordDuplicateOrImpersonationAttempt("190.14.22.10", "1085321456", "Valentina Burbano", "Valentina Burbano"))
            .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> votingService.castVote(req, "190.14.22.10"))
            .isInstanceOf(DuplicateVoteException.class)
            .satisfies(ex -> assertThat(((DuplicateVoteException) ex).getExistingTicket().ticketCode())
                .isEqualTo("TKT-NAC-ABCD123456"));

        verify(votePublisher, never()).publishRawVote(any());
    }

    @Test
    @DisplayName("AAA: Identity impersonation (same cedula, different name) blocks attacker and publishes alert")
    void shouldBlockIdentityImpersonationAndAlert() {
        // Arrange
        VoteTicketRecord existing = new VoteTicketRecord(
            "TKT-NAC-ABCD123456", "1085321456", "Valentina Burbano", "Bogotá",
            Region.CENTRO, ElectionType.NACIONAL, "cand_nac_01", "Elena Vargas",
            VoteSource.REAL, "aws-node-1-us-east-1a", "AABBCCDD11223344", Instant.now()
        );
        VoteRequestRecord impersonator = new VoteRequestRecord(
            "1085321456", "Persona Falsa", "cand_nac_03", "Bogotá", null, "NACIONAL", "190.14.22.99", "REAL"
        );
        SecurityAlertRecord blockedAlert = SecurityAlertRecord.of(
            IpAddress.of("190.14.22.99"), "BLOQUEADA", "SUPLANTACIÓN DE IDENTIDAD BLOQUEADA", 2
        );
        when(stateRepository.getLifecycleStateName()).thenReturn("HABILITADO");
        when(fraudGuard.getBlockReason("190.14.22.99", "1085321456")).thenReturn(Optional.empty());
        when(ticketRepository.findByVoterAndElection(VoterId.of("1085321456"), ElectionType.NACIONAL))
            .thenReturn(Optional.of(existing));
        when(fraudGuard.recordDuplicateOrImpersonationAttempt("190.14.22.99", "1085321456", "Valentina Burbano", "Persona Falsa"))
            .thenReturn(Optional.of(blockedAlert));

        // Act & Assert
        assertThatThrownBy(() -> votingService.castVote(impersonator, "190.14.22.99"))
            .isInstanceOf(SecurityThresholdExceededException.class);
        verify(securityAlertPublisher).publishSecurityAlert(blockedAlert);
        verify(notificationPort).notifyFraudAlert(blockedAlert);
        verify(votePublisher, never()).publishRawVote(any());
    }

    @Test
    @DisplayName("AAA: Strict census mode rejects unregistered voter with VoterNotEligibleException")
    void shouldRejectUnregisteredVoterInStrictMode() {
        // Arrange
        VoteRequestRecord req = new VoteRequestRecord(
            "999888777", "Intruso", "cand_reg_01", "Cali", null, "REGIONAL", "10.0.0.5", "REAL"
        );
        when(stateRepository.getLifecycleStateName()).thenReturn("HABILITADO");
        when(fraudGuard.getBlockReason(anyString(), anyString())).thenReturn(Optional.empty());
        when(stateRepository.isStrictCensusMode()).thenReturn(true);
        when(ticketRepository.findByVoterAndElection(VoterId.of("999888777"), ElectionType.REGIONAL))
            .thenReturn(Optional.empty());
        when(eligibilityStore.isEligible(VoterId.of("999888777"))).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> votingService.castVote(req, "10.0.0.5"))
            .isInstanceOf(VoterNotEligibleException.class);
        verify(votePublisher, never()).publishRawVote(any());
    }

    @Test
    @DisplayName("AAA: GoF State Pattern blocks voting when lifecycle is CERRADO")
    void shouldBlockVoteWhenClosed() {
        VoteRequestRecord req = new VoteRequestRecord("1001234567", "cand_nac_01", "NORTE", "10.0.0.1");
        when(stateRepository.getLifecycleStateName()).thenReturn("CERRADO");

        assertThatThrownBy(() -> votingService.castVote(req, "10.0.0.1"))
            .isInstanceOf(VotingClosedException.class);
        verify(votePublisher, never()).publishRawVote(any());
    }

    @Test
    @DisplayName("AAA: GoF State Pattern blocks voting when lifecycle is EN_AUDITORIA")
    void shouldBlockVoteWhenInAudit() {
        VoteRequestRecord req = new VoteRequestRecord("1001234567", "cand_nac_01", "NORTE", "10.0.0.1");
        when(stateRepository.getLifecycleStateName()).thenReturn("EN_AUDITORIA");

        assertThatThrownBy(() -> votingService.castVote(req, "10.0.0.1"))
            .isInstanceOf(VotingInAuditException.class);
        verify(votePublisher, never()).publishRawVote(any());
    }
}
