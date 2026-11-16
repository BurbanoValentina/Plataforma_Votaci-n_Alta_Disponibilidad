package com.voting.platform.domain.aggregate;

import com.voting.platform.domain.model.CandidateId;
import com.voting.platform.domain.model.Region;
import com.voting.platform.domain.model.RegionalResultRecord;
import com.voting.platform.domain.model.VoteRecord;
import com.voting.platform.domain.model.VoterId;
import com.voting.platform.domain.strategy.RegionalStrategyContext;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Rich Domain Aggregate representing regional vote accumulation.
 * Deduplicates per voter across regions and applies the Strategy Pattern
 * to generate weighted regional results.
 */
public class RegionalVoteCounterAggregate {

    private record VoterRegionalSelection(Region region, CandidateId candidateId) {}

    private final ConcurrentHashMap<VoterId, VoterRegionalSelection> voterSelections;
    private final Map<Region, ConcurrentHashMap<String, LongAdder>> regionalTallies;

    public RegionalVoteCounterAggregate() {
        this.voterSelections = new ConcurrentHashMap<>();
        this.regionalTallies = new EnumMap<>(Region.class);
        for (Region r : Region.values()) {
            this.regionalTallies.put(r, new ConcurrentHashMap<>());
        }
    }

    /**
     * Ingests a vote into regional tallies, preserving deduplication across regions.
     */
    public boolean recordVote(VoteRecord vote) {
        Objects.requireNonNull(vote, "vote cannot be null");
        VoterId voterId = vote.voterId();
        Region newRegion = vote.region();
        CandidateId newCandidate = vote.candidateId();

        synchronized (voterId) {
            VoterRegionalSelection prev = voterSelections.put(voterId, new VoterRegionalSelection(newRegion, newCandidate));

            if (prev == null) {
                // New vote
                regionalTallies.get(newRegion)
                    .computeIfAbsent(newCandidate.value(), k -> new LongAdder())
                    .increment();
                return true;
            } else if (!prev.region().equals(newRegion) || !prev.candidateId().equals(newCandidate)) {
                // Deduplicate: decrement previous region & candidate
                LongAdder prevAdder = regionalTallies.get(prev.region()).get(prev.candidateId().value());
                if (prevAdder != null) {
                    prevAdder.decrement();
                }
                regionalTallies.get(newRegion)
                    .computeIfAbsent(newCandidate.value(), k -> new LongAdder())
                    .increment();
                return true;
            } else {
                // Idempotent
                return false;
            }
        }
    }

    /**
     * Creates an immutable snapshot for a specific region applying the provided Strategy Context.
     */
    public RegionalResultRecord snapshotRegion(Region region, RegionalStrategyContext strategyContext) {
        Objects.requireNonNull(region, "region cannot be null");
        Objects.requireNonNull(strategyContext, "strategyContext cannot be null");

        ConcurrentHashMap<String, LongAdder> tallies = regionalTallies.get(region);
        Map<String, Long> rawVotes = new HashMap<>();
        long totalRegion = 0;

        if (tallies != null) {
            for (Map.Entry<String, LongAdder> entry : tallies.entrySet()) {
                long count = Math.max(0, entry.getValue().sum());
                rawVotes.put(entry.getKey(), count);
                totalRegion += count;
            }
        }

        Map<String, Double> weightedVotes = strategyContext.executeWeighting(region, rawVotes);

        return new RegionalResultRecord(
            region,
            weightedVotes,
            Collections.unmodifiableMap(rawVotes),
            totalRegion,
            Instant.now()
        );
    }

    public long getRawVotes(Region region, String candidateId) {
        ConcurrentHashMap<String, LongAdder> tallies = regionalTallies.get(region);
        if (tallies == null) return 0;
        LongAdder adder = tallies.get(candidateId);
        return adder != null ? Math.max(0, adder.sum()) : 0;
    }
}
