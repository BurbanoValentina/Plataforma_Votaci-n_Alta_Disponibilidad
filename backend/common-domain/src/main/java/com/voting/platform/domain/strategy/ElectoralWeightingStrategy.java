package com.voting.platform.domain.strategy;

import com.voting.platform.domain.model.Region;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Concrete Strategy: Electoral weighting based on predefined regional coefficients.
 */
public class ElectoralWeightingStrategy implements RegionalWeightingStrategy {

    public static final String NAME = "ELECTORAL";

    private final Map<Region, Double> coefficients;

    public ElectoralWeightingStrategy() {
        this.coefficients = new EnumMap<>(Region.class);
        this.coefficients.put(Region.NORTE, 1.25);
        this.coefficients.put(Region.SUR, 1.15);
        this.coefficients.put(Region.ESTE, 0.90);
        this.coefficients.put(Region.OESTE, 1.05);
        this.coefficients.put(Region.CENTRO, 1.40);
    }

    public ElectoralWeightingStrategy(Map<Region, Double> customCoefficients) {
        Objects.requireNonNull(customCoefficients, "customCoefficients cannot be null");
        this.coefficients = new EnumMap<>(customCoefficients);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public double getWeight(Region region) {
        return coefficients.getOrDefault(region, 1.0);
    }
}
