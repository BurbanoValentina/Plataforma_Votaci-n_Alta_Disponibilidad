package com.voting.platform.domain.aggregate;

import com.voting.platform.domain.model.CandidateId;
import com.voting.platform.domain.model.ElectionType;
import com.voting.platform.domain.model.Region;
import com.voting.platform.domain.model.RegionalResultRecord;
import com.voting.platform.domain.model.VoteRecord;
import com.voting.platform.domain.model.VoteSource;
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
 * Deduplicates per voter per election across regions, differentiates REAL vs SIMULATED votes,
 * and applies the GoF Strategy Pattern to compute weighted regional scores.
 */
public class RegionalVoteCounterAggregate {

    private record VoterElectionKey(VoterId voterId, ElectionType electionType) {}
    private record VoterRegionalSelection(Region region, CandidateId candidateId, VoteSource source) {}

    private static final Map<Region, Double> WEIGHT_FACTORS = Map.of(
        Region.NORTE, 1.25,
        Region.SUR, 1.15,
        Region.CENTRO, 1.40,
        Region.ESTE, 0.90,
        Region.OESTE, 1.05
    );

    private final ConcurrentHashMap<VoterElectionKey, VoterRegionalSelection> voterSelections;
    private final Map<Region, ConcurrentHashMap<String, LongAdder>> regionalTallies;
    private final Map<Region, ConcurrentHashMap<String, LongAdder>> regionalRealTallies;
    private final Map<Region, ConcurrentHashMap<String, LongAdder>> regionalSimulatedTallies;

    public RegionalVoteCounterAggregate() {
        this.voterSelections = new ConcurrentHashMap<>();
        this.regionalTallies = new EnumMap<>(Region.class);
        this.regionalRealTallies = new EnumMap<>(Region.class);
        this.regionalSimulatedTallies = new EnumMap<>(Region.class);
        for (Region r : Region.values()) {
            this.regionalTallies.put(r, new ConcurrentHashMap<>());
            this.regionalRealTallies.put(r, new ConcurrentHashMap<>());
            this.regionalSimulatedTallies.put(r, new ConcurrentHashMap<>());
        }
    }

    /**
     * Ingests a vote into regional tallies, preserving deduplication across regions.
     */
    public boolean recordVote(VoteRecord vote) {
        Objects.requireNonNull(vote, "vote cannot be null");
        VoterElectionKey key = new VoterElectionKey(vote.voterId(), vote.electionType());
        VoterRegionalSelection next = new VoterRegionalSelection(vote.region(), vote.candidateId(), vote.voteSource());

        synchronized (key.voterId().value().intern()) {
            VoterRegionalSelection prev = voterSelections.put(key, next);

            if (prev == null) {
                increment(next.region(), next.candidateId().value(), next.source());
                return true;
            } else if (!prev.equals(next)) {
                decrement(prev.region(), prev.candidateId().value(), prev.source());
                increment(next.region(), next.candidateId().value(), next.source());
                return true;
            } else {
                return false;
            }
        }
    }

    private void increment(Region region, String candidateId, VoteSource source) {
        regionalTallies.get(region).computeIfAbsent(candidateId, k -> new LongAdder()).increment();
        if (source == VoteSource.REAL) {
            regionalRealTallies.get(region).computeIfAbsent(candidateId, k -> new LongAdder()).increment();
        } else {
            regionalSimulatedTallies.get(region).computeIfAbsent(candidateId, k -> new LongAdder()).increment();
        }
    }

    private void decrement(Region region, String candidateId, VoteSource source) {
        LongAdder prevAdder = regionalTallies.get(region).get(candidateId);
        if (prevAdder != null) prevAdder.decrement();
        Map<String, LongAdder> srcMap = source == VoteSource.REAL
            ? regionalRealTallies.get(region)
            : regionalSimulatedTallies.get(region);
        LongAdder srcAdder = srcMap.get(candidateId);
        if (srcAdder != null) srcAdder.decrement();
    }

    /**
     * Creates an immutable snapshot for a specific region applying the provided Strategy Context.
     */
    public RegionalResultRecord snapshotRegion(Region region, RegionalStrategyContext strategyContext) {
        Objects.requireNonNull(region, "region cannot be null");
        Objects.requireNonNull(strategyContext, "strategyContext cannot be null");

        Map<String, Long> rawVotes = extractMap(regionalTallies.get(region));
        Map<String, Long> realVotes = extractMap(regionalRealTallies.get(region));
        Map<String, Long> simulatedVotes = extractMap(regionalSimulatedTallies.get(region));

        long totalRegion = rawVotes.values().stream().mapToLong(Long::longValue).sum();
        long totalReal = realVotes.values().stream().mapToLong(Long::longValue).sum();
        long totalSim = simulatedVotes.values().stream().mapToLong(Long::longValue).sum();

        Map<String, Double> weightedVotes = strategyContext.executeWeighting(region, rawVotes);

        return new RegionalResultRecord(
            region,
            weightedVotes,
            Collections.unmodifiableMap(rawVotes),
            Collections.unmodifiableMap(realVotes),
            Collections.unmodifiableMap(simulatedVotes),
            totalRegion,
            totalReal,
            totalSim,
            WEIGHT_FACTORS.getOrDefault(region, 1.0),
            Instant.now()
        );
    }

    private Map<String, Long> extractMap(ConcurrentHashMap<String, LongAdder> tallies) {
        Map<String, Long> out = new HashMap<>();
        if (tallies != null) {
            for (Map.Entry<String, LongAdder> entry : tallies.entrySet()) {
                out.put(entry.getKey(), Math.max(0L, entry.getValue().sum()));
            }
        }
        return out;
    }

    public long getRawVotes(Region region, String candidateId) {
        ConcurrentHashMap<String, LongAdder> tallies = regionalTallies.get(region);
        if (tallies == null) return 0;
        LongAdder adder = tallies.get(candidateId);
        return adder != null ? Math.max(0, adder.sum()) : 0;
    }

    public void clear() {
        voterSelections.clear();
        for (Region r : Region.values()) {
            regionalTallies.get(r).clear();
            regionalRealTallies.get(r).clear();
            regionalSimulatedTallies.get(r).clear();
        }
    }
}
