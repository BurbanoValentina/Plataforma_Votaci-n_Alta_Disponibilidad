package com.voting.platform.regional.service;

import com.voting.platform.domain.aggregate.RegionalVoteCounterAggregate;
import com.voting.platform.domain.model.Region;
import com.voting.platform.domain.model.RegionalResultRecord;
import com.voting.platform.domain.model.VoteRecord;
import com.voting.platform.domain.port.RegionalResultPublisher;
import com.voting.platform.domain.strategy.RegionalStrategyContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Stateful Regional Rollup Service.
 * Aggregates regional counts and applies the GoF Strategy Pattern
 * to compute polymorphic regional weights, publishing to RabbitMQ Topic Exchange.
 */
@Service
public class RegionalRollupService {

    private static final Logger log = LoggerFactory.getLogger(RegionalRollupService.class);

    private final RegionalVoteCounterAggregate regionalAggregate;
    private final RegionalStrategyContext strategyContext;
    private final RegionalResultPublisher regionalResultPublisher;

    public RegionalRollupService(
        RegionalVoteCounterAggregate regionalAggregate,
        RegionalStrategyContext strategyContext,
        RegionalResultPublisher regionalResultPublisher
    ) {
        this.regionalAggregate = Objects.requireNonNull(regionalAggregate, "regionalAggregate cannot be null");
        this.strategyContext = Objects.requireNonNull(strategyContext, "strategyContext cannot be null");
        this.regionalResultPublisher = Objects.requireNonNull(regionalResultPublisher, "regionalResultPublisher cannot be null");
    }

    /**
     * Ingests a vote into regional state with cross-region deduplication.
     */
    public boolean processVote(VoteRecord vote) {
        Objects.requireNonNull(vote, "vote cannot be null");
        return regionalAggregate.recordVote(vote);
    }

    /**
     * Loops through each region, calculates weighted totals via Strategy Pattern,
     * and publishes to RabbitMQ Topic Exchange using specific routing keys (e.g., results.norte).
     */
    public List<RegionalResultRecord> publishAllRegions() {
        List<RegionalResultRecord> publishedSnapshots = new ArrayList<>();

        for (Region region : Region.values()) {
            RegionalResultRecord snapshot = regionalAggregate.snapshotRegion(region, strategyContext);
            String routingKey = region.toRoutingKey();

            regionalResultPublisher.publishRegionalResults(routingKey, snapshot);
            publishedSnapshots.add(snapshot);

            log.info("[REGIONAL DISPATCH] RoutingKey: {}, Region: {}, Raw: {}, Weighted: {}, Strategy: {}",
                routingKey, region, snapshot.rawVotes(), snapshot.weightedVotes(), strategyContext.getCurrentStrategyName());
        }

        return publishedSnapshots;
    }

    public RegionalVoteCounterAggregate getAggregate() {
        return regionalAggregate;
    }

    public RegionalStrategyContext getStrategyContext() {
        return strategyContext;
    }
}
