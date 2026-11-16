package com.voting.platform.dashboard.regional;

import com.voting.platform.dashboard.regional.service.RegionalDashboardService;
import com.voting.platform.domain.model.Region;
import com.voting.platform.domain.model.RegionalResultRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RegionalDashboardService - Real-time Topic Consumer Unit Tests (AAA Pattern)")
class RegionalDashboardServiceTest {

    private RegionalDashboardService service;

    @BeforeEach
    void setUp() {
        service = new RegionalDashboardService();
    }

    @Test
    @DisplayName("Arrange regional snapshot -> Act updateRegionalResult -> Assert state holds updated regional record")
    void shouldUpdateAndHoldRegionalResults() {
        // Arrange
        RegionalResultRecord record = new RegionalResultRecord(
            Region.NORTE,
            Map.of("cand_A", 125.0),
            Map.of("cand_A", 100L),
            100L,
            Instant.now()
        );

        // Act
        service.updateRegionalResult(record);

        // Assert
        RegionalResultRecord retrieved = service.getRegionResult(Region.NORTE);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.region()).isEqualTo(Region.NORTE);
        assertThat(retrieved.totalRegionalVotes()).isEqualTo(100L);
        assertThat(retrieved.weightedVotes().get("cand_A")).isEqualTo(125.0);
    }
}
