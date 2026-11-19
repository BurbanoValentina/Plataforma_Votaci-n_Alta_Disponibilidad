package com.voting.platform.domain.dto.response;

import com.voting.platform.domain.model.CandidateCatalog;
import com.voting.platform.domain.model.ElectionType;
import com.voting.platform.domain.model.Region;
import com.voting.platform.domain.model.VoteSource;
import com.voting.platform.domain.model.VoteTicketCode;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable Value Object representing the official verifiable Vote Ticket returned to citizens
 * upon voting or when attempting a duplicate vote with the same cedula.
 */
public record VoteTicketRecord(
    String ticketCode,
    String voterId,
    String voterName,
    String city,
    Region region,
    ElectionType electionType,
    String candidateId,
    String candidateName,
    VoteSource voteSource,
    String handledByNode,
    String verificationHash,
    Instant votedAt
) {
    public VoteTicketRecord {
        Objects.requireNonNull(ticketCode, "ticketCode cannot be null");
        Objects.requireNonNull(voterId, "voterId cannot be null");
        Objects.requireNonNull(region, "region cannot be null");
        Objects.requireNonNull(electionType, "electionType cannot be null");
        Objects.requireNonNull(candidateId, "candidateId cannot be null");
        if (voterName == null || voterName.isBlank()) voterName = "Ciudadano Verificado";
        if (city == null || city.isBlank()) city = "Bogotá";
        if (candidateName == null || candidateName.isBlank()) {
            candidateName = CandidateCatalog.resolveDisplayName(candidateId);
        }
        if (voteSource == null) voteSource = VoteSource.REAL;
        if (handledByNode == null || handledByNode.isBlank()) handledByNode = "voting-edge";
        if (votedAt == null) votedAt = Instant.now();
        if (verificationHash == null || verificationHash.isBlank()) {
            verificationHash = VoteTicketCode.computeVerificationHash(ticketCode, voterId, candidateId, votedAt);
        }
    }
}

