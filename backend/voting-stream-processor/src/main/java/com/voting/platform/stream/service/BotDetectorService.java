package com.voting.platform.stream.service;

import com.voting.platform.domain.service.BotDetectorAggregate;
import com.voting.platform.domain.model.SecurityAlertRecord;
import com.voting.platform.domain.model.VoteRecord;
import com.voting.platform.domain.messaging.SecurityAlertPublisher;
import com.voting.platform.domain.repository.SecurityIncidentRepositoryPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

/**
 * Stateful Anti-Fraud Service powered by BotDetectorAggregate (GoF State Pattern: NORMAL -> SOSPECHOSA -> BLOQUEADA).
 * Publishes alerts to Kafka security_alerts and persists forensic records in PostgreSQL.
 */
@Service
public class BotDetectorService {

    private final BotDetectorAggregate detectorAggregate;
    private final SecurityAlertPublisher alertPublisher;
    private final SecurityIncidentRepositoryPort incidentRepository;

    @Autowired
    public BotDetectorService(
        SecurityAlertPublisher alertPublisher,
        SecurityIncidentRepositoryPort incidentRepository
    ) {
        this(new BotDetectorAggregate(), alertPublisher, incidentRepository);
    }

    public BotDetectorService(
        BotDetectorAggregate detectorAggregate,
        SecurityAlertPublisher alertPublisher,
        SecurityIncidentRepositoryPort incidentRepository
    ) {
        this.detectorAggregate = Objects.requireNonNull(detectorAggregate, "detectorAggregate cannot be null");
        this.alertPublisher = Objects.requireNonNull(alertPublisher, "alertPublisher cannot be null");
        this.incidentRepository = Objects.requireNonNull(incidentRepository, "incidentRepository cannot be null");
    }

    public Optional<SecurityAlertRecord> analyzeVote(VoteRecord vote) {
        Optional<SecurityAlertRecord> alertOpt = detectorAggregate.inspectVote(vote);
        alertOpt.ifPresent(alert -> {
            alertPublisher.publishSecurityAlert(alert);
            incidentRepository.saveIncident(alert);
        });
        return alertOpt;
    }
}
