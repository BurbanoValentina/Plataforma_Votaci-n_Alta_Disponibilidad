package com.voting.platform.api;

import com.voting.platform.api.model.VoteResponseRecord;
import com.voting.platform.api.service.VotingService;
import com.voting.platform.domain.exception.VoterNotEligibleException;
import com.voting.platform.domain.model.EligibleVoterRecord;
import com.voting.platform.domain.model.Region;
import com.voting.platform.domain.model.VoteRecord;
import com.voting.platform.domain.model.VoteRequestRecord;
import com.voting.platform.domain.model.VoterId;
import com.voting.platform.domain.port.VoteEventPublisher;
import com.voting.platform.domain.port.VoterEligibilityStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("VotingService - Isolated Unit Tests (AAA Pattern with Test Doubles)")
class VotingServiceTest {

    private static class StubEligibilityStore implements VoterEligibilityStore {
        private final Set<VoterId> eligible = new HashSet<>();

        @Override
        public boolean isEligible(VoterId voterId) {
            return eligible.contains(voterId);
        }

        @Override
        public void registerEligible(EligibleVoterRecord record) {
            eligible.add(record.voterId());
        }

        @Override
        public void removeEligible(VoterId voterId) {
            eligible.remove(voterId);
        }

        @Override
        public int size() {
            return eligible.size();
        }

        @Override
        public void clear() {
            eligible.clear();
        }
    }

    private static class StubVotePublisher implements VoteEventPublisher {
        private final List<VoteRecord> published = new ArrayList<>();

        @Override
        public void publishRawVote(VoteRecord vote) {
            published.add(vote);
        }

        public List<VoteRecord> getPublished() {
            return published;
        }

        public int count() {
            return published.size();
        }
    }

    private StubEligibilityStore eligibilityStore;
    private StubVotePublisher votePublisher;
    private VotingService votingService;

    @BeforeEach
    void setUp() {
        eligibilityStore = new StubEligibilityStore();
        votePublisher = new StubVotePublisher();
        votingService = new VotingService(eligibilityStore, votePublisher);

        // Pre-register eligible voters
        eligibilityStore.registerEligible(new EligibleVoterRecord(
            VoterId.of("voter_001"), "Carlos Mendoza", Region.NORTE, Instant.now()
        ));
    }

    @Test
    @DisplayName("Arrange eligible voter -> Act castVote -> Assert returns ACCEPTED and publishes vote to raw_votes")
    void shouldAcceptVoteWhenVoterIsEligible() {
        // Arrange
        VoteRequestRecord request = new VoteRequestRecord("voter_001", "candidate_A", "NORTE", "192.168.1.10");

        // Act
        VoteResponseRecord response = votingService.castVote(request);

        // Assert
        assertThat(response.status()).isEqualTo("ACCEPTED");
        assertThat(response.voterId()).isEqualTo("voter_001");
        assertThat(votePublisher.count()).isEqualTo(1);

        VoteRecord published = votePublisher.getPublished().get(0);
        assertThat(published.voterId().value()).isEqualTo("voter_001");
        assertThat(published.candidateId().value()).isEqualTo("candidate_A");
        assertThat(published.region()).isEqualTo(Region.NORTE);
        assertThat(published.ipAddress().value()).isEqualTo("192.168.1.10");
    }

    @Test
    @DisplayName("Arrange ineligible voter -> Act castVote -> Assert throws VoterNotEligibleException and NEVER publishes")
    void shouldRejectVoteWhenVoterIsNotEligible() {
        // Arrange: voter_999 was never registered in KTable
        VoteRequestRecord request = new VoteRequestRecord("voter_999", "candidate_B", "SUR", "192.168.1.20");

        // Act & Assert
        assertThatThrownBy(() -> votingService.castVote(request))
            .isInstanceOf(VoterNotEligibleException.class)
            .hasMessageContaining("voter_999");

        // Crucial invariant: votePublisher must NEVER receive an unauthorized vote
        assertThat(votePublisher.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("Arrange invalid region or blank inputs -> Act castVote -> Assert Fail-Fast exception")
    void shouldRejectInvalidVoteRequestsFailFast() {
        VoteRequestRecord invalidRegion = new VoteRequestRecord("voter_001", "candA", "UNKNOWN_REGION", "127.0.0.1");
        assertThatThrownBy(() -> votingService.castVote(invalidRegion))
            .isInstanceOf(IllegalArgumentException.class);

        assertThat(votePublisher.count()).isEqualTo(0);
    }
}
