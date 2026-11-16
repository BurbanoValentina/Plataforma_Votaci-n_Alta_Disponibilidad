package com.voting.platform.processor.service;

import com.voting.platform.domain.aggregate.VoteCounterAggregate;
import com.voting.platform.domain.model.GlobalResultRecord;
import com.voting.platform.domain.model.VoteRecord;
import com.voting.platform.domain.port.GlobalResultPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Stateful Global Vote Processor.
 * Ingests incoming raw votes from Kafka, updates the rich domain aggregate (with deduplication),
 * and dispatches periodic consolidated snapshots to RabbitMQ Fanout exchange.
 */
@Service
public class VoteProcessorService {

    private static final Logger log = LoggerFactory.getLogger(VoteProcessorService.class);

    private final VoteCounterAggregate counterAggregate;
    private final GlobalResultPublisher globalResultPublisher;

    public VoteProcessorService(VoteCounterAggregate counterAggregate, GlobalResultPublisher globalResultPublisher) {
        this.counterAggregate = Objects.requireNonNull(counterAggregate, "counterAggregate cannot be null");
        this.globalResultPublisher = Objects.requireNonNull(globalResultPublisher, "globalResultPublisher cannot be null");
    }

    /**
     * Ingests a vote into the domain aggregate, maintaining voter deduplication.
     */
    public boolean processVote(VoteRecord vote) {
        Objects.requireNonNull(vote, "vote cannot be null");
        boolean changed = counterAggregate.recordVote(vote);
        if (changed) {
            log.debug("Aggregate updated: voter={}, candidate={}", vote.voterId(), vote.candidateId());
        } else {
            log.debug("Idempotent vote ignored for voter: {}", vote.voterId());
        }
        return changed;
    }

    /**
     * Snapshots the rich aggregate and publishes to RabbitMQ Fanout Exchange (live_results_global).
     */
    public GlobalResultRecord publishSnapshot() {
        GlobalResultRecord snapshot = counterAggregate.snapshot();
        globalResultPublisher.publishGlobalResults(snapshot);
        log.info("[GLOBAL DISPATCH] Total votes: {}, Standings: {}", snapshot.totalVotes(), snapshot.candidateVotes());
        return snapshot;
    }

    public VoteCounterAggregate getAggregate() {
        return counterAggregate;
    }
}
