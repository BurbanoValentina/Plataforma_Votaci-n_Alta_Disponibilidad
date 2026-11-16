package com.voting.platform.dashboard.global;

import com.voting.platform.dashboard.global.service.GlobalDashboardService;
import com.voting.platform.domain.model.GlobalResultRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GlobalDashboardService - Real-time Fanout Consumer Unit Tests (AAA Pattern)")
class GlobalDashboardServiceTest {

    private GlobalDashboardService service;

    @BeforeEach
    void setUp() {
        service = new GlobalDashboardService();
    }

    @Test
    @DisplayName("Arrange snapshot -> Act updateResults -> Assert state holds latest results for UI clients")
    void shouldUpdateAndHoldLatestGlobalResults() {
        // Arrange
        GlobalResultRecord snapshot = new GlobalResultRecord(
            Map.of("cand_A", 120L, "cand_B", 95L),
            215L,
            Instant.now()
        );

        // Act
        service.updateResults(snapshot);

        // Assert
        GlobalResultRecord latest = service.getLatestResult();
        assertThat(latest.totalVotes()).isEqualTo(215L);
        assertThat(latest.candidateVotes().get("cand_A")).isEqualTo(120L);
        assertThat(latest.candidateVotes().get("cand_B")).isEqualTo(95L);
    }
}
