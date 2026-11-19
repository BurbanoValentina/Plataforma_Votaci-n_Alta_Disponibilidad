package com.voting.platform.domain;

import com.voting.platform.domain.service.RunoffPredictor;
import com.voting.platform.domain.model.ElectionType;
import com.voting.platform.domain.dto.response.GlobalResultRecord;
import com.voting.platform.domain.dto.response.RunoffPredictionRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RunoffPredictorTest {

    @Test
    @DisplayName("AAA: Predicts 2nd Round (Segunda Vuelta) with top 2 finalists when leader <= 50%")
    void shouldPredictRunoffWhenNoCandidateExceedsFiftyPercent() {
        // Arrange
        GlobalResultRecord global = new GlobalResultRecord(
            Map.of("cand_nac_01", 400L, "cand_nac_02", 350L, "cand_nac_03", 150L, "cand_nac_04", 100L),
            Map.of("cand_nac_01", 10L, "cand_nac_02", 20L),
            Map.of("cand_nac_01", 390L, "cand_nac_02", 330L, "cand_nac_03", 150L, "cand_nac_04", 100L),
            Map.of("cand_nac_01", 400L, "cand_nac_02", 350L, "cand_nac_03", 150L, "cand_nac_04", 100L),
            Map.of("cand_nac_01", 10L, "cand_nac_02", 20L),
            Map.of("cand_nac_01", 390L, "cand_nac_02", 330L, "cand_nac_03", 150L, "cand_nac_04", 100L),
            Map.of(), Map.of(), Map.of(),
            1000L, 30L, 970L, 1000L, 0L, Instant.now()
        );

        // Act
        RunoffPredictionRecord allPred = RunoffPredictor.predict(global, ElectionType.NACIONAL, "ALL");
        RunoffPredictionRecord realPred = RunoffPredictor.predict(global, ElectionType.NACIONAL, "REAL");

        // Assert
        assertThat(allPred.runoffRequired()).isTrue();
        assertThat(allPred.finalists()).hasSize(2);
        assertThat(allPred.finalists().get(0).candidateId()).isEqualTo("cand_nac_01");
        assertThat(allPred.finalists().get(1).candidateId()).isEqualTo("cand_nac_02");
        assertThat(allPred.marginOfErrorPct()).isGreaterThan(0.0);

        // In REAL-only mode, cand_nac_02 has 20/30 = 66.67% (>50% -> 1st round win)
        assertThat(realPred.runoffRequired()).isFalse();
        assertThat(realPred.finalists().get(0).candidateId()).isEqualTo("cand_nac_02");
    }
}

