package com.voting.platform.domain;

import com.voting.platform.domain.exception.VotingClosedException;
import com.voting.platform.domain.exception.VotingInAuditException;
import com.voting.platform.domain.model.IpAddress;
import com.voting.platform.domain.state.lifecycle.CerradoState;
import com.voting.platform.domain.state.lifecycle.EnAuditoriaState;
import com.voting.platform.domain.state.lifecycle.HabilitadoState;
import com.voting.platform.domain.state.lifecycle.VotingSession;
import com.voting.platform.domain.state.risk.BlockedRiskState;
import com.voting.platform.domain.state.risk.IpRiskState;
import com.voting.platform.domain.state.risk.IpTracker;
import com.voting.platform.domain.state.risk.NormalRiskState;
import com.voting.platform.domain.state.risk.SuspiciousRiskState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("GoF State Pattern - Unit Tests (AAA)")
class StatePatternTest {

    @Nested
    @DisplayName("Voting Lifecycle State Pattern (HABILITADO, CERRADO, EN_AUDITORIA)")
    class VotingLifecycleTests {

        @Test
        @DisplayName("HabilitadoState: Allows voting, transitions to CerradoState")
        void testHabilitadoState() {
            // Arrange
            HabilitadoState state = HabilitadoState.INSTANCE;

            // Act & Assert
            assertThat(state.isVotingAllowed()).isTrue();
            assertThatCode(state::validateCanVote).doesNotThrowAnyException();
            assertThat(state.transitionToNext()).isEqualTo(CerradoState.INSTANCE);
            assertThat(state.getStateName()).isEqualTo("HABILITADO");
        }

        @Test
        @DisplayName("CerradoState: Rejects voting with VotingClosedException, transitions to EnAuditoriaState")
        void testCerradoState() {
            // Arrange
            CerradoState state = CerradoState.INSTANCE;

            // Act & Assert
            assertThat(state.isVotingAllowed()).isFalse();
            assertThatThrownBy(state::validateCanVote)
                .isInstanceOf(VotingClosedException.class);
            assertThat(state.transitionToNext()).isEqualTo(EnAuditoriaState.INSTANCE);
            assertThat(state.getStateName()).isEqualTo("CERRADO");
        }

        @Test
        @DisplayName("EnAuditoriaState: Rejects voting with VotingInAuditException, transitions to HabilitadoState")
        void testEnAuditoriaState() {
            // Arrange
            EnAuditoriaState state = EnAuditoriaState.INSTANCE;

            // Act & Assert
            assertThat(state.isVotingAllowed()).isFalse();
            assertThatThrownBy(state::validateCanVote)
                .isInstanceOf(VotingInAuditException.class);
            assertThat(state.transitionToNext()).isEqualTo(HabilitadoState.INSTANCE);
            assertThat(state.getStateName()).isEqualTo("EN_AUDITORIA");
        }

        @Test
        @DisplayName("VotingSession: Manages sequential phase progression cleanly")
        void testVotingSessionLifecycle() {
            // Arrange
            VotingSession session = new VotingSession();
            assertThat(session.getCurrentStateName()).isEqualTo("HABILITADO");
            assertThat(session.isVotingAllowed()).isTrue();

            // Act 1: Advance to CERRADO
            session.advanceToNextPhase();
            assertThat(session.getCurrentStateName()).isEqualTo("CERRADO");
            assertThat(session.isVotingAllowed()).isFalse();
            assertThatThrownBy(session::ensureVotingAllowed).isInstanceOf(VotingClosedException.class);

            // Act 2: Advance to EN_AUDITORIA
            session.advanceToNextPhase();
            assertThat(session.getCurrentStateName()).isEqualTo("EN_AUDITORIA");
            assertThat(session.isVotingAllowed()).isFalse();
            assertThatThrownBy(session::ensureVotingAllowed).isInstanceOf(VotingInAuditException.class);

            // Act 3: Advance back to HABILITADO
            session.advanceToNextPhase();
            assertThat(session.getCurrentStateName()).isEqualTo("HABILITADO");
            assertThat(session.isVotingAllowed()).isTrue();
        }
    }

    @Nested
    @DisplayName("IP Risk State Pattern (NORMAL, SOSPECHOSA, BLOQUEADA)")
    class IpRiskStateTests {

        @Test
        @DisplayName("NormalRiskState: Transitions to SOSPECHOSA if >= warning, to BLOQUEADA if >= block")
        void testNormalTransitions() {
            NormalRiskState normal = NormalRiskState.INSTANCE;

            // Below warning (4 votes < 5) -> Normal
            IpRiskState next = normal.evaluateRate(4, 5, 10);
            assertThat(next).isEqualTo(NormalRiskState.INSTANCE);

            // Warning reached (5 votes >= 5) -> Suspicious
            next = normal.evaluateRate(5, 5, 10);
            assertThat(next).isEqualTo(SuspiciousRiskState.INSTANCE);

            // Block reached (10 votes >= 10) -> Blocked
            next = normal.evaluateRate(10, 5, 10);
            assertThat(next).isEqualTo(BlockedRiskState.INSTANCE);
        }

        @Test
        @DisplayName("SuspiciousRiskState: Transitions down to NORMAL if < warning, up to BLOQUEADA if >= block")
        void testSuspiciousTransitions() {
            SuspiciousRiskState suspicious = SuspiciousRiskState.INSTANCE;

            // Rate cooled below warning (< 5) -> Normal
            IpRiskState next = suspicious.evaluateRate(3, 5, 10);
            assertThat(next).isEqualTo(NormalRiskState.INSTANCE);

            // Still between warning and block -> Suspicious
            next = suspicious.evaluateRate(7, 5, 10);
            assertThat(next).isEqualTo(SuspiciousRiskState.INSTANCE);

            // Reached block -> Blocked
            next = suspicious.evaluateRate(10, 5, 10);
            assertThat(next).isEqualTo(BlockedRiskState.INSTANCE);
        }

        @Test
        @DisplayName("BlockedRiskState: Stays blocked while active, steps down to SOSPECHOSA only on 0 votes")
        void testBlockedTransitions() {
            BlockedRiskState blocked = BlockedRiskState.INSTANCE;

            assertThat(blocked.isBlocked()).isTrue();

            // Still has votes in window -> Stays Blocked
            IpRiskState next = blocked.evaluateRate(2, 5, 10);
            assertThat(next).isEqualTo(BlockedRiskState.INSTANCE);

            // Cooldown reached (0 votes in window) -> Steps down to Suspicious
            next = blocked.evaluateRate(0, 5, 10);
            assertThat(next).isEqualTo(SuspiciousRiskState.INSTANCE);
        }

        @Test
        @DisplayName("IpTracker: Sliding window rate calculation and alert triggering")
        void testIpTrackerSlidingWindow() {
            // Arrange
            IpAddress ip = IpAddress.of("192.168.1.100");
            Duration window = Duration.ofSeconds(5);
            IpTracker tracker = new IpTracker(ip, window, 3, 6);

            Instant now = Instant.now();

            // Act 1: 2 votes -> should remain NORMAL
            tracker.recordVote(now);
            IpTracker.EvaluationResult r2 = tracker.recordVote(now.plusMillis(100));
            assertThat(r2.newState().getLevel()).isEqualTo("NORMAL");
            assertThat(r2.requiresAlert()).isFalse();

            // Act 2: 3rd vote -> reaches warning threshold 3 -> transitions to SOSPECHOSA
            IpTracker.EvaluationResult r3 = tracker.recordVote(now.plusMillis(200));
            assertThat(r3.newState().getLevel()).isEqualTo("SOSPECHOSA");
            assertThat(r3.requiresAlert()).isTrue();

            // Act 3: 4th and 5th votes -> remain SOSPECHOSA
            tracker.recordVote(now.plusMillis(300));
            IpTracker.EvaluationResult r5 = tracker.recordVote(now.plusMillis(400));
            assertThat(r5.newState().getLevel()).isEqualTo("SOSPECHOSA");
            assertThat(r5.requiresAlert()).isFalse(); // state didn't change

            // Act 4: 6th vote -> reaches block threshold 6 -> transitions to BLOQUEADA
            IpTracker.EvaluationResult r6 = tracker.recordVote(now.plusMillis(500));
            assertThat(r6.newState().getLevel()).isEqualTo("BLOQUEADA");
            assertThat(r6.requiresAlert()).isTrue();
            assertThat(tracker.isBlocked()).isTrue();
        }
    }
}

