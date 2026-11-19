package com.voting.platform.domain.messaging;

import com.voting.platform.domain.dto.response.GlobalResultRecord;

/**
 * Port interface for publishing global results to RabbitMQ Fanout Exchange (live_results_global).
 */
import com.voting.platform.domain.dto.response.GlobalResultRecord;

public interface GlobalResultPublisher {
    void publishGlobalResults(GlobalResultRecord results);
}

