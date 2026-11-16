package com.voting.platform.domain.model;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable Record representing regional voting results published to RabbitMQ Topic Exchange (live_results_regional).
 */
public record RegionalResultRecord(
    Region region,
    Map<String, Double> weightedVotes,
    Map<String, Long> rawVotes,
    long totalRegionalVotes,
    Instant generatedAt
) {
    public RegionalResultRecord {
        Objects.requireNonNull(region, "region cannot be null");
        Objects.requireNonNull(weightedVotes, "weightedVotes cannot be null");
        Objects.requireNonNull(rawVotes, "rawVotes cannot be null");
        weightedVotes = Collections.unmodifiableMap(Map.copyOf(weightedVotes));
        rawVotes = Collections.unmodifiableMap(Map.copyOf(rawVotes));
        if (generatedAt == null) {
            generatedAt = Instant.now();
        }
    }
}
