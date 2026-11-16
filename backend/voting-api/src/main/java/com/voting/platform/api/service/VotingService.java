package com.voting.platform.api.service;

import com.voting.platform.api.model.VoteResponseRecord;
import com.voting.platform.domain.exception.VoterNotEligibleException;
import com.voting.platform.domain.model.CandidateId;
import com.voting.platform.domain.model.IpAddress;
import com.voting.platform.domain.model.Region;
import com.voting.platform.domain.model.VoteRecord;
import com.voting.platform.domain.model.VoteRequestRecord;
import com.voting.platform.domain.model.VoterId;
import com.voting.platform.domain.port.VoteEventPublisher;
import com.voting.platform.domain.port.VoterEligibilityStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;

/**
 * Core Application Service for Vote Processing in the API Gateway.
 * Enforces DIP through constructor injection.
 * Validates voter eligibility immediately against local in-memory KTable.
 */
@Service
public class VotingService {

    private static final Logger log = LoggerFactory.getLogger(VotingService.class);

    private final VoterEligibilityStore eligibilityStore;
    private final VoteEventPublisher votePublisher;

    public VotingService(VoterEligibilityStore eligibilityStore, VoteEventPublisher votePublisher) {
        this.eligibilityStore = Objects.requireNonNull(eligibilityStore, "eligibilityStore cannot be null");
        this.votePublisher = Objects.requireNonNull(votePublisher, "votePublisher cannot be null");
    }

    /**
     * Ingests and validates an incoming vote request.
     *
     * @param request Validated DTO
     * @return VoteResponseRecord with ACCEPTED status
     * @throws VoterNotEligibleException if user_id is not present in eligible voters state (HTTP 403)
     */
    public VoteResponseRecord castVote(VoteRequestRecord request) {
        Objects.requireNonNull(request, "request cannot be null");

        // Fail-Fast Value Object conversions
        VoterId voterId = VoterId.of(request.voterId());
        CandidateId candidateId = CandidateId.of(request.candidateId());
        Region region = Region.fromString(request.region());
        IpAddress clientIp = IpAddress.of(request.clientIp());

        // Step 1: Immediate local eligibility lookup (KTable in-memory)
        if (!eligibilityStore.isEligible(voterId)) {
            log.warn("Vote rejected! VoterId '{}' is NOT eligible to vote.", voterId);
            throw new VoterNotEligibleException(voterId);
        }

        // Step 2: Construct immutable domain vote event
        VoteRecord voteRecord = new VoteRecord(voterId, candidateId, region, clientIp, Instant.now());

        // Step 3: Publish to Kafka raw_votes compacted topic with user_id as key
        votePublisher.publishRawVote(voteRecord);
        log.info("Vote accepted: voter={}, candidate={}, region={}", voterId, candidateId, region);

        return VoteResponseRecord.accepted(voterId.value());
    }
}
