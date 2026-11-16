package com.voting.platform.domain.model;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable Record representing the consolidated global voting results
 * broadcasted to RabbitMQ Fanout Exchange (live_results_global).
 */
public record GlobalResultRecord(
    Map<String, Long> candidateVotes,
    long totalVotes,
    Instant generatedAt
) {
    public GlobalResultRecord {
        Objects.requireNonNull(candidateVotes, "candidateVotes cannot be null");
        candidateVotes = Collections.unmodifiableMap(Map.copyOf(candidateVotes));
        if (generatedAt == null) {
            generatedAt = Instant.now();
        }
    }
}
