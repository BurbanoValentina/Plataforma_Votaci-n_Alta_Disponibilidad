package com.voting.platform.stream.service;

import com.voting.platform.domain.aggregate.RegionalVoteCounterAggregate;
import com.voting.platform.domain.model.Region;
import com.voting.platform.domain.model.RegionalResultRecord;
import com.voting.platform.domain.model.VoteRecord;
import com.voting.platform.domain.port.RegionalResultPublisher;
import com.voting.platform.domain.strategy.ElectoralWeightingStrategy;
import com.voting.platform.domain.strategy.RegionalStrategyContext;
import com.voting.platform.domain.strategy.RegionalWeightingStrategy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Stateful Regional Rollup Service.
 * Applies the GoF Strategy Pattern (ElectoralWeightingStrategy) and publishes
 * regional weighted tallies every 5s to RabbitMQ Topic Exchange (results.<region>).
 */
@Service
public class RegionalRollupService {

    private final RegionalVoteCounterAggregate regionalAggregate;
    private final RegionalStrategyContext strategyContext;
    private final RegionalResultPublisher resultPublisher;

    public RegionalRollupService(RegionalResultPublisher resultPublisher) {
        this(
            new RegionalVoteCounterAggregate(),
            new RegionalStrategyContext(new ElectoralWeightingStrategy()),
            resultPublisher
        );
    }

    public RegionalRollupService(
        RegionalVoteCounterAggregate regionalAggregate,
        RegionalStrategyContext strategyContext,
        RegionalResultPublisher resultPublisher
    ) {
        this.regionalAggregate = Objects.requireNonNull(regionalAggregate, "regionalAggregate cannot be null");
        this.strategyContext = Objects.requireNonNull(strategyContext, "strategyContext cannot be null");
        this.resultPublisher = Objects.requireNonNull(resultPublisher, "resultPublisher cannot be null");
    }

    public boolean processRegionalVote(VoteRecord vote) {
        return regionalAggregate.recordVote(vote);
    }

    public void changeWeightingStrategy(RegionalWeightingStrategy strategy) {
        this.strategyContext.setStrategy(strategy);
    }

    @Scheduled(fixedRate = 5000)
    public void publishAllRegionalSnapshots() {
        for (Region region : Region.values()) {
            RegionalResultRecord snapshot = regionalAggregate.snapshotRegion(region, strategyContext);
            resultPublisher.publishRegionalResults(region.toRoutingKey(), snapshot);
        }
    }

    public RegionalResultRecord snapshotFor(Region region) {
        return regionalAggregate.snapshotRegion(region, strategyContext);
    }
}
