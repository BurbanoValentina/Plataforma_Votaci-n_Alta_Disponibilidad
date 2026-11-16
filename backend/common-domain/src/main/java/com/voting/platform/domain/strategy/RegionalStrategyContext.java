package com.voting.platform.domain.strategy;

import com.voting.platform.domain.model.Region;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe Strategy Context allowing dynamic switching of weighting algorithms at runtime.
 */
public class RegionalStrategyContext {

    private final AtomicReference<RegionalWeightingStrategy> currentStrategy;

    public RegionalStrategyContext(RegionalWeightingStrategy initialStrategy) {
        Objects.requireNonNull(initialStrategy, "initialStrategy cannot be null");
        this.currentStrategy = new AtomicReference<>(initialStrategy);
    }

    public RegionalStrategyContext() {
        this(new UniformWeightingStrategy());
    }

    public void setStrategy(RegionalWeightingStrategy newStrategy) {
        Objects.requireNonNull(newStrategy, "newStrategy cannot be null");
        this.currentStrategy.set(newStrategy);
    }

    public RegionalWeightingStrategy getStrategy() {
        return this.currentStrategy.get();
    }

    public String getCurrentStrategyName() {
        return this.currentStrategy.get().getName();
    }

    public Map<String, Double> executeWeighting(Region region, Map<String, Long> rawVotes) {
        return this.currentStrategy.get().applyWeighting(region, rawVotes);
    }

    public double getRegionalWeight(Region region) {
        return this.currentStrategy.get().getWeight(region);
    }
}
