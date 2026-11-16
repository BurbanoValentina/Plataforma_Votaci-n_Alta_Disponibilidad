package com.voting.platform.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable Value Object representing an eligible voter stored in the Kafka KTable (eligible_voters).
 */
public record EligibleVoterRecord(
    VoterId voterId,
    String fullName,
    Region region,
    Instant registeredAt
) {
    public EligibleVoterRecord {
        Objects.requireNonNull(voterId, "voterId cannot be null");
        Objects.requireNonNull(fullName, "fullName cannot be null");
        if (fullName.isBlank()) {
            throw new IllegalArgumentException("fullName cannot be blank");
        }
        Objects.requireNonNull(region, "region cannot be null");
        if (registeredAt == null) {
            registeredAt = Instant.now();
        }
    }

    public static EligibleVoterRecord of(String voterId, String fullName, String region, Instant registeredAt) {
        return new EligibleVoterRecord(
            VoterId.of(voterId),
            fullName,
            Region.fromString(region),
            registeredAt
        );
    }
}
