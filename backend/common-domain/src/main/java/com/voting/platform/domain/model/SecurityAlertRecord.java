package com.voting.platform.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable Event Record representing a fraud / security alert emitted to Kafka security_alerts
 * and persisted to PostgreSQL for forensic audit.
 */
public record SecurityAlertRecord(
    String alertId,
    IpAddress ipAddress,
    String riskLevel,
    String reason,
    int voteCountInWindow,
    Instant timestamp
) {
    public SecurityAlertRecord {
        if (alertId == null || alertId.isBlank()) {
            alertId = UUID.randomUUID().toString();
        }
        Objects.requireNonNull(ipAddress, "ipAddress cannot be null");
        Objects.requireNonNull(riskLevel, "riskLevel cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }

    public static SecurityAlertRecord of(IpAddress ipAddress, String riskLevel, String reason, int count) {
        return new SecurityAlertRecord(
            UUID.randomUUID().toString(),
            ipAddress,
            riskLevel,
            reason,
            count,
            Instant.now()
        );
    }
}
