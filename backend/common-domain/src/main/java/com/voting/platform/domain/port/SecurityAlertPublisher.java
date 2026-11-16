package com.voting.platform.domain.port;

import com.voting.platform.domain.model.SecurityAlertRecord;

/**
 * Port interface for publishing security alerts to Kafka topic security_alerts.
 */
public interface SecurityAlertPublisher {
    void publishSecurityAlert(SecurityAlertRecord alert);
}
