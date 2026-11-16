package com.voting.platform.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable Domain Event / Value Object representing an accepted vote.
 * Used across Kafka raw_votes topic, aggregates, anti-fraud, and persistence.
 */
public record VoteRecord(
    VoterId voterId,
    CandidateId candidateId,
    Region region,
    IpAddress ipAddress,
    Instant timestamp
) {
    public VoteRecord {
        Objects.requireNonNull(voterId, "voterId cannot be null");
        Objects.requireNonNull(candidateId, "candidateId cannot be null");
        Objects.requireNonNull(region, "region cannot be null");
        Objects.requireNonNull(ipAddress, "ipAddress cannot be null");
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }

    public static VoteRecord of(
        String voterId,
        String candidateId,
        String region,
        String clientIp,
        Instant timestamp
    ) {
        return new VoteRecord(
            VoterId.of(voterId),
            CandidateId.of(candidateId),
            Region.fromString(region),
            IpAddress.of(clientIp),
            timestamp
        );
    }
}
