package com.voting.platform.domain;

import com.voting.platform.domain.model.Region;
import com.voting.platform.domain.strategy.ElectoralWeightingStrategy;
import com.voting.platform.domain.strategy.RegionalStrategyContext;
import com.voting.platform.domain.strategy.RegionalWeightingStrategy;
import com.voting.platform.domain.strategy.UniformWeightingStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("GoF Strategy Pattern - Regional Weighting Unit Tests (AAA)")
class StrategyPatternTest {

    private Map<String, Long> rawVotes;

    @BeforeEach
    void setUp() {
        rawVotes = new HashMap<>();
        rawVotes.put("candidate_A", 100L);
        rawVotes.put("candidate_B", 200L);
    }

    @Test
    @DisplayName("UniformWeightingStrategy: Multiplies votes by 1.0 across all regions")
    void shouldApplyUniformWeighting() {
        // Arrange
        RegionalWeightingStrategy strategy = new UniformWeightingStrategy();

        // Act
        Map<String, Double> resultsNorte = strategy.applyWeighting(Region.NORTE, rawVotes);
        Map<String, Double> resultsCentro = strategy.applyWeighting(Region.CENTRO, rawVotes);

        // Assert
        assertThat(strategy.getName()).isEqualTo("UNIFORM");
        assertThat(resultsNorte.get("candidate_A")).isEqualTo(100.0);
        assertThat(resultsNorte.get("candidate_B")).isEqualTo(200.0);
        assertThat(resultsCentro.get("candidate_A")).isEqualTo(100.0);
    }

    @Test
    @DisplayName("ElectoralWeightingStrategy: Correctly applies differential weights per region")
    void shouldApplyElectoralWeighting() {
        // Arrange
        RegionalWeightingStrategy strategy = new ElectoralWeightingStrategy();

        // Act
        // NORTE weight = 1.25 -> 100 * 1.25 = 125.0, 200 * 1.25 = 250.0
        Map<String, Double> resultsNorte = strategy.applyWeighting(Region.NORTE, rawVotes);
        // CENTRO weight = 1.40 -> 100 * 1.40 = 140.0
        Map<String, Double> resultsCentro = strategy.applyWeighting(Region.CENTRO, rawVotes);
        // ESTE weight = 0.90 -> 100 * 0.90 = 90.0
        Map<String, Double> resultsEste = strategy.applyWeighting(Region.ESTE, rawVotes);

        // Assert
        assertThat(strategy.getName()).isEqualTo("ELECTORAL");
        assertThat(resultsNorte.get("candidate_A")).isEqualTo(125.0);
        assertThat(resultsNorte.get("candidate_B")).isEqualTo(250.0);
        assertThat(resultsCentro.get("candidate_A")).isEqualTo(140.0);
        assertThat(resultsEste.get("candidate_A")).isEqualTo(90.0);
    }

    @Test
    @DisplayName("RegionalStrategyContext: Dynamically hot-swaps weighting strategies at runtime")
    void shouldHotSwapStrategiesInContext() {
        // Arrange
        RegionalStrategyContext context = new RegionalStrategyContext(new UniformWeightingStrategy());
        assertThat(context.getCurrentStrategyName()).isEqualTo("UNIFORM");

        // Act 1: Execute with Uniform
        Map<String, Double> firstRun = context.executeWeighting(Region.NORTE, rawVotes);
        assertThat(firstRun.get("candidate_A")).isEqualTo(100.0);

        // Act 2: Switch to Electoral strategy
        context.setStrategy(new ElectoralWeightingStrategy());
        Map<String, Double> secondRun = context.executeWeighting(Region.NORTE, rawVotes);

        // Assert
        assertThat(context.getCurrentStrategyName()).isEqualTo("ELECTORAL");
        assertThat(secondRun.get("candidate_A")).isEqualTo(125.0);
    }

    @Test
    @DisplayName("RegionalStrategyContext: Rejects null strategy")
    void shouldRejectNullStrategy() {
        RegionalStrategyContext context = new RegionalStrategyContext();
        assertThatThrownBy(() -> context.setStrategy(null))
            .isInstanceOf(NullPointerException.class);
    }
}
