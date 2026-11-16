package com.voting.platform.regional;

import com.voting.platform.domain.aggregate.RegionalVoteCounterAggregate;
import com.voting.platform.domain.model.CandidateId;
import com.voting.platform.domain.model.IpAddress;
import com.voting.platform.domain.model.Region;
import com.voting.platform.domain.model.RegionalResultRecord;
import com.voting.platform.domain.model.VoteRecord;
import com.voting.platform.domain.model.VoterId;
import com.voting.platform.domain.port.RegionalResultPublisher;
import com.voting.platform.domain.strategy.ElectoralWeightingStrategy;
import com.voting.platform.domain.strategy.RegionalStrategyContext;
import com.voting.platform.regional.service.RegionalRollupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RegionalRollupService - Strategy Pattern & Topic Routing Unit Tests (AAA Pattern)")
class RegionalRollupServiceTest {

    private static class RecordingRegionalResultPublisher implements RegionalResultPublisher {
        private final Map<String, RegionalResultRecord> published = new HashMap<>();

        @Override
        public void publishRegionalResults(String routingKey, RegionalResultRecord results) {
            published.put(routingKey, results);
        }

        public RegionalResultRecord getByRoutingKey(String routingKey) {
            return published.get(routingKey);
        }

        public int count() {
            return published.size();
        }
    }

    private RegionalVoteCounterAggregate aggregate;
    private RegionalStrategyContext strategyContext;
    private RecordingRegionalResultPublisher publisher;
    private RegionalRollupService service;

    @BeforeEach
    void setUp() {
        aggregate = new RegionalVoteCounterAggregate();
        // Uses Electoral strategy (NORTE multiplier = 1.25, CENTRO multiplier = 1.40)
        strategyContext = new RegionalStrategyContext(new ElectoralWeightingStrategy());
        publisher = new RecordingRegionalResultPublisher();
        service = new RegionalRollupService(aggregate, strategyContext, publisher);
    }

    @Test
    @DisplayName("Arrange votes in different regions -> Act publishAllRegions -> Assert Strategy Pattern weighting and routing keys")
    void shouldCalculateWeightedResultsAndPublishToTopicExchange() {
        // Arrange
        VoteRecord voteNorte1 = new VoteRecord(VoterId.of("voter_001"), CandidateId.of("cand_A"), Region.NORTE, IpAddress.of("10.0.0.1"), Instant.now());
        VoteRecord voteNorte2 = new VoteRecord(VoterId.of("voter_002"), CandidateId.of("cand_A"), Region.NORTE, IpAddress.of("10.0.0.2"), Instant.now());
        VoteRecord voteCentro = new VoteRecord(VoterId.of("voter_003"), CandidateId.of("cand_B"), Region.CENTRO, IpAddress.of("10.0.0.3"), Instant.now());

        // Act
        service.processVote(voteNorte1);
        service.processVote(voteNorte2);
        service.processVote(voteCentro);

        List<RegionalResultRecord> snapshots = service.publishAllRegions();

        // Assert: 5 regions published (NORTE, SUR, ESTE, OESTE, CENTRO)
        assertThat(snapshots).hasSize(5);
        assertThat(publisher.count()).isEqualTo(5);

        // Verify NORTE: 2 raw votes for cand_A, weighted with 1.25 -> 2.50
        RegionalResultRecord norteResult = publisher.getByRoutingKey("results.norte");
        assertThat(norteResult).isNotNull();
        assertThat(norteResult.region()).isEqualTo(Region.NORTE);
        assertThat(norteResult.totalRegionalVotes()).isEqualTo(2);
        assertThat(norteResult.rawVotes().get("cand_A")).isEqualTo(2L);
        assertThat(norteResult.weightedVotes().get("cand_A")).isEqualTo(2.50);

        // Verify CENTRO: 1 raw vote for cand_B, weighted with 1.40 -> 1.40
        RegionalResultRecord centroResult = publisher.getByRoutingKey("results.centro");
        assertThat(centroResult).isNotNull();
        assertThat(centroResult.region()).isEqualTo(Region.CENTRO);
        assertThat(centroResult.totalRegionalVotes()).isEqualTo(1);
        assertThat(centroResult.rawVotes().get("cand_B")).isEqualTo(1L);
        assertThat(centroResult.weightedVotes().get("cand_B")).isEqualTo(1.40);
    }
}
