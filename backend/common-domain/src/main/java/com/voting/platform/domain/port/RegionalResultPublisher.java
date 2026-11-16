package com.voting.platform.domain.port;

import com.voting.platform.domain.model.RegionalResultRecord;

/**
 * Port interface for publishing regional results to RabbitMQ Topic Exchange (live_results_regional).
 */
public interface RegionalResultPublisher {
    void publishRegionalResults(String routingKey, RegionalResultRecord results);
}
