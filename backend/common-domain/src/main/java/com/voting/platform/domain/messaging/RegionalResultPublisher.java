package com.voting.platform.domain.messaging;

import com.voting.platform.domain.dto.response.RegionalResultRecord;

/**
 * Port interface for publishing regional results to RabbitMQ Topic Exchange (live_results_regional).
 */
import com.voting.platform.domain.dto.response.RegionalResultRecord;

public interface RegionalResultPublisher {
    void publishRegionalResults(String routingKey, RegionalResultRecord results);
}

