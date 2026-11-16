package com.voting.platform.regional.scheduler;

import com.voting.platform.regional.service.RegionalRollupService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Scheduled worker that executes every 5 seconds (5000ms) to publish
 * regional breakdown results to RabbitMQ Topic exchange (live_results_regional).
 */
@Component
public class RegionalRollupScheduler {

    private final RegionalRollupService regionalRollupService;

    public RegionalRollupScheduler(RegionalRollupService regionalRollupService) {
        this.regionalRollupService = Objects.requireNonNull(regionalRollupService, "regionalRollupService cannot be null");
    }

    @Scheduled(fixedRate = 5000)
    public void publishEveryFiveSeconds() {
        regionalRollupService.publishAllRegions();
    }
}
