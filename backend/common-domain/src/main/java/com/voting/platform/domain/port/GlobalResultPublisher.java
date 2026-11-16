package com.voting.platform.domain.port;

import com.voting.platform.domain.model.GlobalResultRecord;

/**
 * Port interface for publishing global results to RabbitMQ Fanout Exchange (live_results_global).
 */
public interface GlobalResultPublisher {
    void publishGlobalResults(GlobalResultRecord results);
}
