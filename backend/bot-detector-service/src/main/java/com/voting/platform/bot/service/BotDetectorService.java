package com.voting.platform.bot.service;

import com.voting.platform.domain.aggregate.BotDetectorAggregate;
import com.voting.platform.domain.model.SecurityAlertRecord;
import com.voting.platform.domain.model.VoteRecord;
import com.voting.platform.domain.port.SecurityAlertPublisher;
import com.voting.platform.domain.port.SecurityIncidentRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

/**
 * Stateful Anti-Fraud Bot Detector Service.
 * Tracks per-IP voting frequency and transitions between GoF States (NORMAL, SOSPECHOSA, BLOQUEADA).
 * When risk is detected, produces events to Kafka (security_alerts) and persists to PostgreSQL.
 */
@Service
public class BotDetectorService {

    private static final Logger log = LoggerFactory.getLogger(BotDetectorService.class);

    private final BotDetectorAggregate detectorAggregate;
    private final SecurityAlertPublisher alertPublisher;
    private final SecurityIncidentRepositoryPort incidentRepository;

    public BotDetectorService(
        BotDetectorAggregate detectorAggregate,
        SecurityAlertPublisher alertPublisher,
        SecurityIncidentRepositoryPort incidentRepository
    ) {
        this.detectorAggregate = Objects.requireNonNull(detectorAggregate, "detectorAggregate cannot be null");
        this.alertPublisher = Objects.requireNonNull(alertPublisher, "alertPublisher cannot be null");
        this.incidentRepository = Objects.requireNonNull(incidentRepository, "incidentRepository cannot be null");
    }

    /**
     * Inspects a vote from raw_votes. Evaluates rate and state transitions.
     */
    public Optional<SecurityAlertRecord> inspectVote(VoteRecord vote) {
        Objects.requireNonNull(vote, "vote cannot be null");

        Optional<SecurityAlertRecord> alertOpt = detectorAggregate.inspectVote(vote);

        alertOpt.ifPresent(alert -> {
            log.warn("Anomaly detected for IP {}: level={}. Publishing alert and archiving incident.",
                alert.ipAddress(), alert.riskLevel());

            // Output 1: Publish to Kafka security_alerts topic
            alertPublisher.publishSecurityAlert(alert);

            // Output 2: Persist incident in PostgreSQL for forensic audit
            incidentRepository.saveIncident(alert);
        });

        return alertOpt;
    }

    public BotDetectorAggregate getDetectorAggregate() {
        return detectorAggregate;
    }
}
