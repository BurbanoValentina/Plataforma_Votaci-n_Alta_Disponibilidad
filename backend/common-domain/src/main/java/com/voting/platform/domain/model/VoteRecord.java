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
    Instant timestamp,
    String city,
    ElectionType electionType,
    VoteSource voteSource,
    String ticketCode,
    String handledByNode
) {
    public VoteRecord {
        Objects.requireNonNull(voterId, "voterId cannot be null");
        Objects.requireNonNull(candidateId, "candidateId cannot be null");
        Objects.requireNonNull(region, "region cannot be null");
        Objects.requireNonNull(ipAddress, "ipAddress cannot be null");
        if (timestamp == null) {
            timestamp = Instant.now();
        }
        if (city == null || city.isBlank()) {
            city = "Bogotá";
        }
        if (electionType == null) {
            electionType = ElectionType.NACIONAL;
        }
        if (voteSource == null) {
            voteSource = VoteSource.REAL;
        }
        if (ticketCode == null || ticketCode.isBlank()) {
            ticketCode = VoteTicketCode.generate(electionType).value();
        }
        if (handledByNode == null || handledByNode.isBlank()) {
            handledByNode = "voting-edge";
        }
    }

    /**
     * Backward-compatible 5-arg constructor for pure unit tests.
     */
    public VoteRecord(
        VoterId voterId,
        CandidateId candidateId,
        Region region,
        IpAddress ipAddress,
        Instant timestamp
    ) {
        this(
            voterId,
            candidateId,
            region,
            ipAddress,
            timestamp,
            "Bogotá",
            ElectionType.NACIONAL,
            VoteSource.REAL,
            VoteTicketCode.generate(ElectionType.NACIONAL).value(),
            "voting-edge"
        );
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

    public static VoteRecord create(
        String voterId,
        String candidateId,
        String city,
        String region,
        String electionType,
        String voteSource,
        String clientIp,
        String ticketCode,
        String handledByNode,
        Instant timestamp
    ) {
        ElectionType resolvedElection = ElectionType.fromString(electionType);
        Region resolvedRegion = City.resolveRegionFor(city, region);
        return new VoteRecord(
            VoterId.of(voterId),
            CandidateId.of(candidateId),
            resolvedRegion,
            IpAddress.of(clientIp),
            timestamp,
            (city != null && !city.isBlank()) ? city.trim() : "Bogotá",
            resolvedElection,
            VoteSource.fromString(voteSource),
            ticketCode,
            handledByNode
        );
    }

    /**
     * Composite Kafka deduplication key: ensures 1 vote per voter per election type.
     */
    public String kafkaKey() {
        return voterId.value() + ":" + electionType.name();
    }
}
