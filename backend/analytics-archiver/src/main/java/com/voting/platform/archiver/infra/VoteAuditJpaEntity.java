package com.voting.platform.archiver.infra;

import com.voting.platform.domain.model.VoteRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/**
 * Rich JPA Entity modeling an immutable audit trail entry of a vote cast in the system.
 * Prevents anemic design with encapsulated fields and fail-fast construction.
 */
@Entity
@Table(name = "audit_votes")
public class VoteAuditJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "voter_id", nullable = false, length = 64)
    private String voterId;

    @Column(name = "candidate_id", nullable = false, length = 64)
    private String candidateId;

    @Column(name = "region", nullable = false, length = 32)
    private String region;

    @Column(name = "ip_address", nullable = false, length = 64)
    private String ipAddress;

    @Column(name = "voted_at", nullable = false)
    private Instant votedAt;

    @Column(name = "archived_at", nullable = false)
    private Instant archivedAt;

    protected VoteAuditJpaEntity() {
        // JPA specification
    }

    public VoteAuditJpaEntity(
        String voterId,
        String candidateId,
        String region,
        String ipAddress,
        Instant votedAt,
        Instant archivedAt
    ) {
        this.voterId = Objects.requireNonNull(voterId, "voterId cannot be null");
        this.candidateId = Objects.requireNonNull(candidateId, "candidateId cannot be null");
        this.region = Objects.requireNonNull(region, "region cannot be null");
        this.ipAddress = Objects.requireNonNull(ipAddress, "ipAddress cannot be null");
        this.votedAt = (votedAt != null) ? votedAt : Instant.now();
        this.archivedAt = (archivedAt != null) ? archivedAt : Instant.now();
    }

    public static VoteAuditJpaEntity fromVote(VoteRecord vote) {
        Objects.requireNonNull(vote, "vote cannot be null");
        return new VoteAuditJpaEntity(
            vote.voterId().value(),
            vote.candidateId().value(),
            vote.region().name(),
            vote.ipAddress().value(),
            vote.timestamp(),
            Instant.now()
        );
    }

    public Long getId() {
        return id;
    }

    public String getVoterId() {
        return voterId;
    }

    public String getCandidateId() {
        return candidateId;
    }

    public String getRegion() {
        return region;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public Instant getVotedAt() {
        return votedAt;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }
}
