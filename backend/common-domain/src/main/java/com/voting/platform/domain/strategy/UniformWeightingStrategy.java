package com.voting.platform.domain.strategy;

import com.voting.platform.domain.model.Region;

/**
 * Concrete Strategy: Uniform 1:1 weighting across all regions.
 */
public class UniformWeightingStrategy implements RegionalWeightingStrategy {

    public static final String NAME = "UNIFORM";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public double getWeight(Region region) {
        return 1.0;
    }
}
