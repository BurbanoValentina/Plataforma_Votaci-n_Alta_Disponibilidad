package com.voting.platform.domain.service;

import com.voting.platform.domain.model.CandidateId;
import com.voting.platform.domain.model.ElectionType;
import com.voting.platform.domain.dto.response.GlobalResultRecord;
import com.voting.platform.domain.model.VoteRecord;
import com.voting.platform.domain.model.VoteSource;
import com.voting.platform.domain.model.VoterId;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Rich Domain Aggregate representing the Global Vote Counter.
 * Protects business invariants regarding voter uniqueness per election,
 * deduplication (last vote wins), election separation (NACIONAL vs REGIONAL),
 * and source differentiation (REAL vs SIMULATED).
 */
public class VoteCounterAggregate {

    private record VoterElectionKey(VoterId voterId, ElectionType electionType) {}
    private record RecordedSelection(CandidateId candidateId, VoteSource source) {}

    private final ConcurrentHashMap<VoterElectionKey, RecordedSelection> voterRegistry;
    private final ConcurrentHashMap<String, LongAdder> candidateTallies;
    private final ConcurrentHashMap<String, LongAdder> realTallies;
    private final ConcurrentHashMap<String, LongAdder> simulatedTallies;

    private final ConcurrentHashMap<String, LongAdder> nationalTallies;
    private final ConcurrentHashMap<String, LongAdder> nationalRealTallies;
    private final ConcurrentHashMap<String, LongAdder> nationalSimulatedTallies;

    private final ConcurrentHashMap<String, LongAdder> regionalTallies;
    private final ConcurrentHashMap<String, LongAdder> regionalRealTallies;
    private final ConcurrentHashMap<String, LongAdder> regionalSimulatedTallies;

    public VoteCounterAggregate() {
        this.voterRegistry = new ConcurrentHashMap<>();
        this.candidateTallies = new ConcurrentHashMap<>();
        this.realTallies = new ConcurrentHashMap<>();
        this.simulatedTallies = new ConcurrentHashMap<>();
        this.nationalTallies = new ConcurrentHashMap<>();
        this.nationalRealTallies = new ConcurrentHashMap<>();
        this.nationalSimulatedTallies = new ConcurrentHashMap<>();
        this.regionalTallies = new ConcurrentHashMap<>();
        this.regionalRealTallies = new ConcurrentHashMap<>();
        this.regionalSimulatedTallies = new ConcurrentHashMap<>();
    }

    /**
     * Ingests a vote, enforcing per-election deduplication ("last vote wins")
     * while accurately separating REAL vs SIMULATED and NACIONAL vs REGIONAL tallies.
     */
    public boolean recordVote(VoteRecord vote) {
        Objects.requireNonNull(vote, "vote cannot be null");
        VoterElectionKey key = new VoterElectionKey(vote.voterId(), vote.electionType());
        RecordedSelection next = new RecordedSelection(vote.candidateId(), vote.voteSource());

        synchronized (key.voterId().value().intern()) {
            RecordedSelection previous = voterRegistry.put(key, next);
            if (previous == null) {
                incrementTallies(vote.electionType(), next.candidateId().value(), next.source());
                return true;
            } else if (!previous.equals(next)) {
                decrementTallies(vote.electionType(), previous.candidateId().value(), previous.source());
                incrementTallies(vote.electionType(), next.candidateId().value(), next.source());
                return true;
            } else {
                return false;
            }
        }
    }

    private void incrementTallies(ElectionType type, String candidateId, VoteSource source) {
        candidateTallies.computeIfAbsent(candidateId, k -> new LongAdder()).increment();
        if (source == VoteSource.REAL) {
            realTallies.computeIfAbsent(candidateId, k -> new LongAdder()).increment();
        } else {
            simulatedTallies.computeIfAbsent(candidateId, k -> new LongAdder()).increment();
        }

        if (type == ElectionType.NACIONAL) {
            nationalTallies.computeIfAbsent(candidateId, k -> new LongAdder()).increment();
            if (source == VoteSource.REAL) {
                nationalRealTallies.computeIfAbsent(candidateId, k -> new LongAdder()).increment();
            } else {
                nationalSimulatedTallies.computeIfAbsent(candidateId, k -> new LongAdder()).increment();
            }
        } else {
            regionalTallies.computeIfAbsent(candidateId, k -> new LongAdder()).increment();
            if (source == VoteSource.REAL) {
                regionalRealTallies.computeIfAbsent(candidateId, k -> new LongAdder()).increment();
            } else {
                regionalSimulatedTallies.computeIfAbsent(candidateId, k -> new LongAdder()).increment();
            }
        }
    }

    private void decrementTallies(ElectionType type, String candidateId, VoteSource source) {
        decrement(candidateTallies, candidateId);
        if (source == VoteSource.REAL) {
            decrement(realTallies, candidateId);
        } else {
            decrement(simulatedTallies, candidateId);
        }

        if (type == ElectionType.NACIONAL) {
            decrement(nationalTallies, candidateId);
            if (source == VoteSource.REAL) {
                decrement(nationalRealTallies, candidateId);
            } else {
                decrement(nationalSimulatedTallies, candidateId);
            }
        } else {
            decrement(regionalTallies, candidateId);
            if (source == VoteSource.REAL) {
                decrement(regionalRealTallies, candidateId);
            } else {
                decrement(regionalSimulatedTallies, candidateId);
            }
        }
    }

    private void decrement(ConcurrentHashMap<String, LongAdder> map, String key) {
        LongAdder adder = map.get(key);
        if (adder != null) {
            adder.decrement();
        }
    }

    private Map<String, Long> toCountMap(ConcurrentHashMap<String, LongAdder> source) {
        Map<String, Long> result = new HashMap<>();
        for (Map.Entry<String, LongAdder> entry : source.entrySet()) {
            result.put(entry.getKey(), Math.max(0L, entry.getValue().sum()));
        }
        return Collections.unmodifiableMap(result);
    }

    private long sumValues(Map<String, Long> map) {
        long sum = 0;
        for (Long v : map.values()) {
            sum += v;
        }
        return sum;
    }

    /**
     * Generates an immutable snapshot of current global results.
     */
    public GlobalResultRecord snapshot() {
        Map<String, Long> totalMap = toCountMap(candidateTallies);
        Map<String, Long> realMap = toCountMap(realTallies);
        Map<String, Long> simMap = toCountMap(simulatedTallies);

        Map<String, Long> nacMap = toCountMap(nationalTallies);
        Map<String, Long> nacRealMap = toCountMap(nationalRealTallies);
        Map<String, Long> nacSimMap = toCountMap(nationalSimulatedTallies);

        Map<String, Long> regMap = toCountMap(regionalTallies);
        Map<String, Long> regRealMap = toCountMap(regionalRealTallies);
        Map<String, Long> regSimMap = toCountMap(regionalSimulatedTallies);

        return new GlobalResultRecord(
            totalMap,
            realMap,
            simMap,
            nacMap,
            nacRealMap,
            nacSimMap,
            regMap,
            regRealMap,
            regSimMap,
            sumValues(totalMap),
            sumValues(realMap),
            sumValues(simMap),
            sumValues(nacMap),
            sumValues(regMap),
            Instant.now()
        );
    }

    public long getTotalUniqueVoters() {
        return voterRegistry.size();
    }

    public long getVotesForCandidate(String candidateId) {
        LongAdder adder = candidateTallies.get(candidateId);
        return adder != null ? Math.max(0, adder.sum()) : 0;
    }

    public void clear() {
        voterRegistry.clear();
        candidateTallies.clear();
        realTallies.clear();
        simulatedTallies.clear();
        nationalTallies.clear();
        nationalRealTallies.clear();
        nationalSimulatedTallies.clear();
        regionalTallies.clear();
        regionalRealTallies.clear();
        regionalSimulatedTallies.clear();
    }
}

