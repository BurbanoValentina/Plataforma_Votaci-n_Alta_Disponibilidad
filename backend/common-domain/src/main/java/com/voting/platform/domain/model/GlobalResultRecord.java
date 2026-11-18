package com.voting.platform.domain.model;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable Record representing the consolidated global voting results
 * broadcasted to RabbitMQ Fanout Exchange (live_results_global).
 * Differentiates between REAL citizen votes and SIMULATED live traffic,
 * and breaks down National vs Regional elections.
 */
public record GlobalResultRecord(
    Map<String, Long> candidateVotes,
    Map<String, Long> realCandidateVotes,
    Map<String, Long> simulatedCandidateVotes,
    Map<String, Long> nationalCandidateVotes,
    Map<String, Long> nationalRealVotes,
    Map<String, Long> nationalSimulatedVotes,
    Map<String, Long> regionalCandidateVotes,
    Map<String, Long> regionalRealVotes,
    Map<String, Long> regionalSimulatedVotes,
    long totalVotes,
    long totalRealVotes,
    long totalSimulatedVotes,
    long totalNationalVotes,
    long totalRegionalVotes,
    Instant generatedAt
) {
    public GlobalResultRecord {
        Objects.requireNonNull(candidateVotes, "candidateVotes cannot be null");
        candidateVotes = Collections.unmodifiableMap(Map.copyOf(candidateVotes));
        realCandidateVotes = realCandidateVotes == null ? Map.of() : Collections.unmodifiableMap(Map.copyOf(realCandidateVotes));
        simulatedCandidateVotes = simulatedCandidateVotes == null ? Map.of() : Collections.unmodifiableMap(Map.copyOf(simulatedCandidateVotes));
        nationalCandidateVotes = nationalCandidateVotes == null ? Map.of() : Collections.unmodifiableMap(Map.copyOf(nationalCandidateVotes));
        nationalRealVotes = nationalRealVotes == null ? Map.of() : Collections.unmodifiableMap(Map.copyOf(nationalRealVotes));
        nationalSimulatedVotes = nationalSimulatedVotes == null ? Map.of() : Collections.unmodifiableMap(Map.copyOf(nationalSimulatedVotes));
        regionalCandidateVotes = regionalCandidateVotes == null ? Map.of() : Collections.unmodifiableMap(Map.copyOf(regionalCandidateVotes));
        regionalRealVotes = regionalRealVotes == null ? Map.of() : Collections.unmodifiableMap(Map.copyOf(regionalRealVotes));
        regionalSimulatedVotes = regionalSimulatedVotes == null ? Map.of() : Collections.unmodifiableMap(Map.copyOf(regionalSimulatedVotes));
        if (generatedAt == null) {
            generatedAt = Instant.now();
        }
    }

    /**
     * Backward-compatible 3-argument constructor.
     */
    public GlobalResultRecord(Map<String, Long> candidateVotes, long totalVotes, Instant generatedAt) {
        this(
            candidateVotes,
            candidateVotes,
            Map.of(),
            candidateVotes,
            candidateVotes,
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            totalVotes,
            totalVotes,
            0L,
            totalVotes,
            0L,
            generatedAt
        );
    }

    public static GlobalResultRecord empty() {
        return new GlobalResultRecord(
            Map.of(), Map.of(), Map.of(),
            Map.of(), Map.of(), Map.of(),
            Map.of(), Map.of(), Map.of(),
            0L, 0L, 0L, 0L, 0L,
            Instant.now()
        );
    }
}
