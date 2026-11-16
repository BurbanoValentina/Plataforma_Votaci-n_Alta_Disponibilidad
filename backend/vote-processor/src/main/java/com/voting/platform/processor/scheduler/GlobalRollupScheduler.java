package com.voting.platform.processor.scheduler;

import com.voting.platform.processor.service.VoteProcessorService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Scheduled worker that executes every 1 second (1000ms) to publish
 * consolidated global voting results to RabbitMQ Fanout exchange.
 */
@Component
public class GlobalRollupScheduler {

    private final VoteProcessorService voteProcessorService;

    public GlobalRollupScheduler(VoteProcessorService voteProcessorService) {
        this.voteProcessorService = Objects.requireNonNull(voteProcessorService, "voteProcessorService cannot be null");
    }

    @Scheduled(fixedRate = 1000)
    public void publishEverySecond() {
        voteProcessorService.publishSnapshot();
    }
}
