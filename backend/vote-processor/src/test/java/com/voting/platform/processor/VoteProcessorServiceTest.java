package com.voting.platform.processor;

import com.voting.platform.domain.aggregate.VoteCounterAggregate;
import com.voting.platform.domain.model.CandidateId;
import com.voting.platform.domain.model.GlobalResultRecord;
import com.voting.platform.domain.model.IpAddress;
import com.voting.platform.domain.model.Region;
import com.voting.platform.domain.model.VoteRecord;
import com.voting.platform.domain.model.VoterId;
import com.voting.platform.domain.port.GlobalResultPublisher;
import com.voting.platform.processor.service.VoteProcessorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("VoteProcessorService - Stateful Global Counter Unit Tests (AAA Pattern)")
class VoteProcessorServiceTest {

    private static class RecordingGlobalResultPublisher implements GlobalResultPublisher {
        private final List<GlobalResultRecord> published = new ArrayList<>();

        @Override
        public void publishGlobalResults(GlobalResultRecord results) {
            published.add(results);
        }

        public List<GlobalResultRecord> getPublished() {
            return published;
        }

        public int count() {
            return published.size();
        }
    }

    private VoteCounterAggregate aggregate;
    private RecordingGlobalResultPublisher publisher;
    private VoteProcessorService service;

    @BeforeEach
    void setUp() {
        aggregate = new VoteCounterAggregate();
        publisher = new RecordingGlobalResultPublisher();
        service = new VoteProcessorService(aggregate, publisher);
    }

    @Test
    @DisplayName("Arrange votes with revote -> Act processVotes & publishSnapshot -> Assert deduplication and fanout")
    void shouldProcessVotesAndPublishConsolidatedSnapshot() {
        // Arrange
        VoterId voter1 = VoterId.of("voter_001");
        VoterId voter2 = VoterId.of("voter_002");
        CandidateId candA = CandidateId.of("cand_A");
        CandidateId candB = CandidateId.of("cand_B");

        VoteRecord vote1 = new VoteRecord(voter1, candA, Region.NORTE, IpAddress.of("127.0.0.1"), Instant.now());
        VoteRecord vote2 = new VoteRecord(voter2, candB, Region.SUR, IpAddress.of("127.0.0.2"), Instant.now());
        // Voter 1 changes mind to Candidate B (Deduplication)
        VoteRecord revote1 = new VoteRecord(voter1, candB, Region.NORTE, IpAddress.of("127.0.0.1"), Instant.now());

        // Act
        service.processVote(vote1);
        service.processVote(vote2);
        service.processVote(revote1);

        GlobalResultRecord snapshot = service.publishSnapshot();

        // Assert
        assertThat(snapshot.totalVotes()).isEqualTo(2);
        assertThat(snapshot.candidateVotes().get("cand_A")).isEqualTo(0L);
        assertThat(snapshot.candidateVotes().get("cand_B")).isEqualTo(2L);

        // Verify Fanout Publisher received exact consolidated record
        assertThat(publisher.count()).isEqualTo(1);
        GlobalResultRecord published = publisher.getPublished().get(0);
        assertThat(published.totalVotes()).isEqualTo(2);
        assertThat(published.candidateVotes().get("cand_B")).isEqualTo(2L);
    }
}
