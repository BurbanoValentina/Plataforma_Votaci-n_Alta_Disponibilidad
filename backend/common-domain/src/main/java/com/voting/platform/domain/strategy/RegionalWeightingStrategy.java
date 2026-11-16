package com.voting.platform.domain.strategy;

import com.voting.platform.domain.model.Region;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Strategy interface for polymorphic regional weighting algorithms.
 */
public interface RegionalWeightingStrategy {

    /**
     * Unique identifier for the strategy.
     */
    String getName();

    /**
     * Weight multiplier assigned to the given region.
     */
    double getWeight(Region region);

    /**
     * Applies regional weighting to raw candidate tallies.
     *
     * @param region   The region being processed.
     * @param rawVotes Map of candidateId to raw vote counts.
     * @return Map of candidateId to weighted vote scores.
     */
    default Map<String, Double> applyWeighting(Region region, Map<String, Long> rawVotes) {
        if (rawVotes == null || rawVotes.isEmpty()) {
            return Collections.emptyMap();
        }
        double weight = getWeight(region);
        Map<String, Double> weighted = new HashMap<>();
        for (Map.Entry<String, Long> entry : rawVotes.entrySet()) {
            weighted.put(entry.getKey(), Math.round((entry.getValue() * weight) * 100.0) / 100.0);
        }
        return Collections.unmodifiableMap(weighted);
    }
}
