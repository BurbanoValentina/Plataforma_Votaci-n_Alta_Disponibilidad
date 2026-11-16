package com.voting.platform.bot.infra;

import com.voting.platform.domain.model.SecurityAlertRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Rich JPA Entity modeling a forensic security / fraud incident.
 * Encapsulates state and protects invariants without passive/anemic setters.
 */
@Entity
@Table(name = "security_incidents")
public class SecurityIncidentJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 64)
    private String id;

    @Column(name = "ip_address", nullable = false, length = 64)
    private String ipAddress;

    @Column(name = "risk_level", nullable = false, length = 32)
    private String riskLevel;

    @Column(name = "reason", nullable = false, length = 512)
    private String reason;

    @Column(name = "vote_count", nullable = false)
    private int voteCount;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    protected SecurityIncidentJpaEntity() {
        // JPA required protected constructor
    }

    public SecurityIncidentJpaEntity(
        String id,
        String ipAddress,
        String riskLevel,
        String reason,
        int voteCount,
        Instant detectedAt
    ) {
        this.id = (id != null && !id.isBlank()) ? id : UUID.randomUUID().toString();
        this.ipAddress = Objects.requireNonNull(ipAddress, "ipAddress cannot be null");
        this.riskLevel = Objects.requireNonNull(riskLevel, "riskLevel cannot be null");
        this.reason = Objects.requireNonNull(reason, "reason cannot be null");
        this.voteCount = voteCount;
        this.detectedAt = (detectedAt != null) ? detectedAt : Instant.now();
    }

    public static SecurityIncidentJpaEntity fromAlert(SecurityAlertRecord alert) {
        Objects.requireNonNull(alert, "alert cannot be null");
        return new SecurityIncidentJpaEntity(
            alert.alertId(),
            alert.ipAddress().value(),
            alert.riskLevel(),
            alert.reason(),
            alert.voteCountInWindow(),
            alert.timestamp()
        );
    }

    public String getId() {
        return id;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public String getReason() {
        return reason;
    }

    public int getVoteCount() {
        return voteCount;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }
}
