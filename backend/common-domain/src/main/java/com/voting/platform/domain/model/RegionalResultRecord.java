package com.voting.platform.domain.model;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable Record representing regional voting results published to RabbitMQ Topic Exchange
 * (live_results_regional), preserving GoF Strategy weighting and REAL vs SIMULATED breakdown.
 */
public record RegionalResultRecord(
    Region region,
    Map<String, Double> weightedVotes,
    Map<String, Long> rawVotes,
    Map<String, Long> realVotes,
    Map<String, Long> simulatedVotes,
    long totalRegionalVotes,
    long totalRealVotes,
    long totalSimulatedVotes,
    double weightMultiplier,
    Instant generatedAt
) {
    public RegionalResultRecord {
        Objects.requireNonNull(region, "region cannot be null");
        Objects.requireNonNull(weightedVotes, "weightedVotes cannot be null");
        Objects.requireNonNull(rawVotes, "rawVotes cannot be null");
        weightedVotes = Collections.unmodifiableMap(Map.copyOf(weightedVotes));
        rawVotes = Collections.unmodifiableMap(Map.copyOf(rawVotes));
        realVotes = realVotes == null ? Map.of() : Collections.unmodifiableMap(Map.copyOf(realVotes));
        simulatedVotes = simulatedVotes == null ? Map.of() : Collections.unmodifiableMap(Map.copyOf(simulatedVotes));
        if (generatedAt == null) {
            generatedAt = Instant.now();
        }
    }

    /**
     * Backward-compatible 5-argument constructor.
     */
    public RegionalResultRecord(
        Region region,
        Map<String, Double> weightedVotes,
        Map<String, Long> rawVotes,
        long totalRegionalVotes,
        Instant generatedAt
    ) {
        this(region, weightedVotes, rawVotes, rawVotes, Map.of(), totalRegionalVotes, totalRegionalVotes, 0L, 1.0, generatedAt);
    }
}
