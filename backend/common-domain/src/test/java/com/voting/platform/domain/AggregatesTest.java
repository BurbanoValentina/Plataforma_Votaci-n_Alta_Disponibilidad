package com.voting.platform.domain;

import com.voting.platform.domain.service.BotDetectorAggregate;
import com.voting.platform.domain.service.RegionalVoteCounterAggregate;
import com.voting.platform.domain.service.VoteCounterAggregate;
import com.voting.platform.domain.model.CandidateId;
import com.voting.platform.domain.dto.response.GlobalResultRecord;
import com.voting.platform.domain.model.IpAddress;
import com.voting.platform.domain.model.Region;
import com.voting.platform.domain.dto.response.RegionalResultRecord;
import com.voting.platform.domain.model.SecurityAlertRecord;
import com.voting.platform.domain.model.VoteRecord;
import com.voting.platform.domain.model.VoterId;
import com.voting.platform.domain.strategy.ElectoralWeightingStrategy;
import com.voting.platform.domain.strategy.RegionalStrategyContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Domain Aggregates - Rich POO Business Logic Unit Tests")
class AggregatesTest {

    @Nested
    @DisplayName("VoteCounterAggregate: Deduplication & Global Counting")
    class VoteCounterAggregateTests {

        @Test
        @DisplayName("Should ingest votes, deduplicate voter (last vote wins) and decrement previous candidate")
        void shouldHandleDeduplicationCorrectly() {
            // Arrange
            VoteCounterAggregate aggregate = new VoteCounterAggregate();
            VoterId voter1 = VoterId.of("voter_1");
            CandidateId candA = CandidateId.of("candidate_A");
            CandidateId candB = CandidateId.of("candidate_B");

            // Act 1: Voter 1 votes for Candidate A
            VoteRecord firstVote = new VoteRecord(voter1, candA, Region.NORTE, IpAddress.of("192.168.1.1"), Instant.now());
            boolean altered1 = aggregate.recordVote(firstVote);

            // Assert 1
            assertThat(altered1).isTrue();
            assertThat(aggregate.getVotesForCandidate("candidate_A")).isEqualTo(1);
            assertThat(aggregate.getVotesForCandidate("candidate_B")).isEqualTo(0);
            assertThat(aggregate.getTotalUniqueVoters()).isEqualTo(1);

            // Act 2: Voter 1 changes mind and votes for Candidate B (Deduplication / Last Vote Wins)
            VoteRecord secondVote = new VoteRecord(voter1, candB, Region.NORTE, IpAddress.of("192.168.1.1"), Instant.now());
            boolean altered2 = aggregate.recordVote(secondVote);

            // Assert 2: Candidate A decremented to 0, Candidate B incremented to 1, total unique voters STILL 1!
            assertThat(altered2).isTrue();
            assertThat(aggregate.getVotesForCandidate("candidate_A")).isEqualTo(0);
            assertThat(aggregate.getVotesForCandidate("candidate_B")).isEqualTo(1);
            assertThat(aggregate.getTotalUniqueVoters()).isEqualTo(1);

            // Act 3: Voter 1 votes for Candidate B again (Idempotency)
            boolean altered3 = aggregate.recordVote(secondVote);
            assertThat(altered3).isFalse();
            assertThat(aggregate.getVotesForCandidate("candidate_B")).isEqualTo(1);

            // Snapshot check
            GlobalResultRecord snapshot = aggregate.snapshot();
            assertThat(snapshot.totalVotes()).isEqualTo(1);
            assertThat(snapshot.candidateVotes().get("candidate_B")).isEqualTo(1L);
        }

        @Test
        @DisplayName("Concurrency: Concurrent votes from different voters are accurately tallied")
        void shouldHandleConcurrentVotes() throws InterruptedException {
            // Arrange
            VoteCounterAggregate aggregate = new VoteCounterAggregate();
            int threadCount = 20;
            int votesPerThread = 50;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            // Act
            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        for (int v = 0; v < votesPerThread; v++) {
                            VoterId voterId = VoterId.of("voter_" + threadId + "_" + v);
                            CandidateId cand = (v % 2 == 0) ? CandidateId.of("candidate_A") : CandidateId.of("candidate_B");
                            VoteRecord vote = new VoteRecord(voterId, cand, Region.CENTRO, IpAddress.of("127.0.0.1"), Instant.now());
                            aggregate.recordVote(vote);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            // Assert
            long expectedTotal = (long) threadCount * votesPerThread;
            assertThat(aggregate.getTotalUniqueVoters()).isEqualTo(expectedTotal);
            assertThat(aggregate.snapshot().totalVotes()).isEqualTo(expectedTotal);
            assertThat(aggregate.getVotesForCandidate("candidate_A")).isEqualTo(expectedTotal / 2);
            assertThat(aggregate.getVotesForCandidate("candidate_B")).isEqualTo(expectedTotal / 2);
        }
    }

    @Nested
    @DisplayName("RegionalVoteCounterAggregate: Regional Tallies & Strategy Weighting")
    class RegionalVoteCounterAggregateTests {

        @Test
        @DisplayName("Should tally votes per region and generate weighted snapshot with Strategy Pattern")
        void shouldTallyAndWeightPerRegion() {
            // Arrange
            RegionalVoteCounterAggregate aggregate = new RegionalVoteCounterAggregate();
            VoterId v1 = VoterId.of("voter_01");
            VoterId v2 = VoterId.of("voter_02");
            CandidateId candA = CandidateId.of("cand_A");

            // Act: 2 votes in NORTE for cand_A
            aggregate.recordVote(new VoteRecord(v1, candA, Region.NORTE, IpAddress.of("10.0.0.1"), Instant.now()));
            aggregate.recordVote(new VoteRecord(v2, candA, Region.NORTE, IpAddress.of("10.0.0.2"), Instant.now()));

            RegionalStrategyContext strategyContext = new RegionalStrategyContext(new ElectoralWeightingStrategy());
            // NORTE weight = 1.25 -> 2 raw votes * 1.25 = 2.50 weighted votes
            RegionalResultRecord snapshot = aggregate.snapshotRegion(Region.NORTE, strategyContext);

            // Assert
            assertThat(snapshot.region()).isEqualTo(Region.NORTE);
            assertThat(snapshot.totalRegionalVotes()).isEqualTo(2);
            assertThat(snapshot.rawVotes().get("cand_A")).isEqualTo(2L);
            assertThat(snapshot.weightedVotes().get("cand_A")).isEqualTo(2.50);
        }

        @Test
        @DisplayName("Should handle voter changing region and candidate (deduplication across regions)")
        void shouldHandleCrossRegionDeduplication() {
            // Arrange
            RegionalVoteCounterAggregate aggregate = new RegionalVoteCounterAggregate();
            VoterId v1 = VoterId.of("voter_01");
            CandidateId candA = CandidateId.of("cand_A");
            CandidateId candB = CandidateId.of("cand_B");

            // Vote in NORTE for cand_A
            aggregate.recordVote(new VoteRecord(v1, candA, Region.NORTE, IpAddress.of("10.0.0.1"), Instant.now()));
            assertThat(aggregate.getRawVotes(Region.NORTE, "cand_A")).isEqualTo(1);

            // Voter re-votes in SUR for cand_B
            aggregate.recordVote(new VoteRecord(v1, candB, Region.SUR, IpAddress.of("10.0.0.1"), Instant.now()));

            // NORTE cand_A should be decremented to 0; SUR cand_B should be incremented to 1
            assertThat(aggregate.getRawVotes(Region.NORTE, "cand_A")).isEqualTo(0);
            assertThat(aggregate.getRawVotes(Region.SUR, "cand_B")).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("BotDetectorAggregate: Anti-Fraud and Anomaly Detection")
    class BotDetectorAggregateTests {

        @Test
        @DisplayName("Should emit security alert when IP voting rate crosses anomaly threshold")
        void shouldDetectAnomalyAndEmitAlert() {
            // Arrange
            BotDetectorAggregate detector = new BotDetectorAggregate(Duration.ofSeconds(10), 3, 6);
            IpAddress ip = IpAddress.of("192.168.1.99");
            Instant now = Instant.now();

            // Act 1: Send 2 votes (below warning threshold 3)
            VoteRecord vote1 = new VoteRecord(VoterId.of("voter_01"), CandidateId.of("candA"), Region.SUR, ip, now);
            VoteRecord vote2 = new VoteRecord(VoterId.of("voter_02"), CandidateId.of("candA"), Region.SUR, ip, now.plusMillis(100));

            Optional<SecurityAlertRecord> alert1 = detector.inspectVote(vote1);
            Optional<SecurityAlertRecord> alert2 = detector.inspectVote(vote2);

            assertThat(alert1).isEmpty();
            assertThat(alert2).isEmpty();

            // Act 2: Send 3rd vote (reaches warning threshold 3 -> SOSPECHOSA)
            VoteRecord vote3 = new VoteRecord(VoterId.of("voter_03"), CandidateId.of("candA"), Region.SUR, ip, now.plusMillis(200));
            Optional<SecurityAlertRecord> alert3 = detector.inspectVote(vote3);

            // Assert: Alert triggered!
            assertThat(alert3).isPresent();
            SecurityAlertRecord alert = alert3.get();
            assertThat(alert.ipAddress()).isEqualTo(ip);
            assertThat(alert.riskLevel()).isEqualTo("SOSPECHOSA");
            assertThat(alert.voteCountInWindow()).isEqualTo(3);
        }
    }
}

