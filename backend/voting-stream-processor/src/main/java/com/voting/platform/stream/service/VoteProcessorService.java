package com.voting.platform.stream.service;

import com.voting.platform.domain.service.VoteCounterAggregate;
import com.voting.platform.domain.dto.response.GlobalResultRecord;
import com.voting.platform.domain.model.VoteRecord;
import com.voting.platform.domain.messaging.GlobalResultPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Stateful Global Vote Counter Service.
 * Delegates deduplication & REAL/SIMULATED separation to VoteCounterAggregate
 * and broadcasts snapshots every 1s to RabbitMQ Fanout Exchange.
 */
@Service
public class VoteProcessorService {

    private final VoteCounterAggregate counterAggregate;
    private final GlobalResultPublisher resultPublisher;

    @Autowired
    public VoteProcessorService(GlobalResultPublisher resultPublisher) {
        this(new VoteCounterAggregate(), resultPublisher);
    }

    public VoteProcessorService(VoteCounterAggregate counterAggregate, GlobalResultPublisher resultPublisher) {
        this.counterAggregate = Objects.requireNonNull(counterAggregate, "counterAggregate cannot be null");
        this.resultPublisher = Objects.requireNonNull(resultPublisher, "resultPublisher cannot be null");
    }

    public boolean processVote(VoteRecord vote) {
        return counterAggregate.recordVote(vote);
    }

    @Scheduled(fixedRate = 1000)
    public GlobalResultRecord publishGlobalSnapshot() {
        GlobalResultRecord snapshot = counterAggregate.snapshot();
        resultPublisher.publishGlobalResults(snapshot);
        return snapshot;
    }

    public GlobalResultRecord currentSnapshot() {
        return counterAggregate.snapshot();
    }
}
