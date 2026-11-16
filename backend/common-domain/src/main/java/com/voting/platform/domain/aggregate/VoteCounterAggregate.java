package com.voting.platform.domain.aggregate;

import com.voting.platform.domain.model.CandidateId;
import com.voting.platform.domain.model.GlobalResultRecord;
import com.voting.platform.domain.model.VoteRecord;
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
 * Protects business invariants regarding voter uniqueness, deduplication (last vote wins),
 * and thread-safe tallies without anemic data structures.
 */
public class VoteCounterAggregate {

    private final ConcurrentHashMap<VoterId, CandidateId> voterRegistry;
    private final ConcurrentHashMap<String, LongAdder> candidateTallies;

    public VoteCounterAggregate() {
        this.voterRegistry = new ConcurrentHashMap<>();
        this.candidateTallies = new ConcurrentHashMap<>();
    }

    /**
     * Ingests a vote, enforcing deduplication and the "last vote wins" invariant.
     *
     * @param vote The accepted vote record.
     * @return true if the vote altered the candidate standings, false if idempotent.
     */
    public boolean recordVote(VoteRecord vote) {
        Objects.requireNonNull(vote, "vote cannot be null");
        VoterId voterId = vote.voterId();
        CandidateId newCandidate = vote.candidateId();

        synchronized (voterId) {
            CandidateId previousCandidate = voterRegistry.put(voterId, newCandidate);

            if (previousCandidate == null) {
                // First vote by this voter
                candidateTallies.computeIfAbsent(newCandidate.value(), k -> new LongAdder()).increment();
                return true;
            } else if (!previousCandidate.equals(newCandidate)) {
                // Changed vote: decrement previous candidate and increment new candidate
                LongAdder prevAdder = candidateTallies.get(previousCandidate.value());
                if (prevAdder != null) {
                    prevAdder.decrement();
                }
                candidateTallies.computeIfAbsent(newCandidate.value(), k -> new LongAdder()).increment();
                return true;
            } else {
                // Idempotent vote for same candidate
                return false;
            }
        }
    }

    /**
     * Generates an immutable snapshot of current global results.
     */
    public GlobalResultRecord snapshot() {
        Map<String, Long> standings = new HashMap<>();
        long total = 0;

        for (Map.Entry<String, LongAdder> entry : candidateTallies.entrySet()) {
            long count = Math.max(0, entry.getValue().sum());
            standings.put(entry.getKey(), count);
            total += count;
        }

        return new GlobalResultRecord(Collections.unmodifiableMap(standings), total, Instant.now());
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
    }
}
