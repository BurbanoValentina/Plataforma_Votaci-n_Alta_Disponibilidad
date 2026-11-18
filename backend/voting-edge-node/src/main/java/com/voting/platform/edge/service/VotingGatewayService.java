package com.voting.platform.edge.service;

import com.voting.platform.domain.exception.DuplicateVoteException;
import com.voting.platform.domain.exception.VoterNotEligibleException;
import com.voting.platform.domain.model.CandidateCatalog;
import com.voting.platform.domain.model.CandidateId;
import com.voting.platform.domain.model.City;
import com.voting.platform.domain.model.ElectionType;
import com.voting.platform.domain.model.EligibleVoterRecord;
import com.voting.platform.domain.model.IpAddress;
import com.voting.platform.domain.model.Region;
import com.voting.platform.domain.model.VoteRecord;
import com.voting.platform.domain.model.VoteRequestRecord;
import com.voting.platform.domain.model.VoteSource;
import com.voting.platform.domain.model.VoteTicketCode;
import com.voting.platform.domain.model.VoteTicketRecord;
import com.voting.platform.domain.model.VoterId;
import com.voting.platform.domain.port.ElectionStateRepositoryPort;
import com.voting.platform.domain.port.EligibleVoterPublisher;
import com.voting.platform.domain.port.VoteEventPublisher;
import com.voting.platform.domain.port.VoteTicketRepositoryPort;
import com.voting.platform.domain.port.VoterEligibilityStore;
import com.voting.platform.domain.state.lifecycle.CerradoState;
import com.voting.platform.domain.state.lifecycle.EnAuditoriaState;
import com.voting.platform.domain.state.lifecycle.HabilitadoState;
import com.voting.platform.domain.state.lifecycle.VotingLifecycleState;
import com.voting.platform.domain.state.lifecycle.VotingSession;
import com.voting.platform.edge.infra.InMemoryEligibilityStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Core Application Service in each load-balanced Voting Edge Node.
 * Enforces DIP via constructor injection:
 * 1. Validates GoF State Pattern lifecycle (HABILITADO / EN_AUDITORIA / CERRADO)
 * 2. Enforces single-vote-per-cedula-per-election with official Ticket return on duplicates
 * 3. Validates voter eligibility against local Kafka-materialized KTable
 * 4. Publishes verified VoteRecord to Kafka compacted topic raw_votes
 */
@Service
public class VotingGatewayService {

    private static final Logger log = LoggerFactory.getLogger(VotingGatewayService.class);

    private final VoterEligibilityStore eligibilityStore;
    private final EligibleVoterPublisher eligibleVoterPublisher;
    private final VoteEventPublisher votePublisher;
    private final VoteTicketRepositoryPort ticketRepository;
    private final ElectionStateRepositoryPort stateRepository;
    private final String nodeId;

    public VotingGatewayService(
        VoterEligibilityStore eligibilityStore,
        EligibleVoterPublisher eligibleVoterPublisher,
        VoteEventPublisher votePublisher,
        VoteTicketRepositoryPort ticketRepository,
        ElectionStateRepositoryPort stateRepository,
        @Value("${voting.node-id:voting-edge-1}") String nodeId
    ) {
        this.eligibilityStore = Objects.requireNonNull(eligibilityStore, "eligibilityStore cannot be null");
        this.eligibleVoterPublisher = Objects.requireNonNull(eligibleVoterPublisher, "eligibleVoterPublisher cannot be null");
        this.votePublisher = Objects.requireNonNull(votePublisher, "votePublisher cannot be null");
        this.ticketRepository = Objects.requireNonNull(ticketRepository, "ticketRepository cannot be null");
        this.stateRepository = Objects.requireNonNull(stateRepository, "stateRepository cannot be null");
        this.nodeId = nodeId != null && !nodeId.isBlank() ? nodeId : "voting-edge-1";
    }

    public VoteTicketRecord castVote(VoteRequestRecord request, String resolvedClientIp) {
        Objects.requireNonNull(request, "request cannot be null");

        // 1. GoF State Pattern check (HABILITADO / EN_AUDITORIA / CERRADO)
        VotingSession session = new VotingSession(resolveLifecycleState(stateRepository.getLifecycleStateName()));
        session.ensureVotingAllowed();

        // 2. Fail-Fast Value Object validation
        VoterId voterId = VoterId.of(request.voterId());
        CandidateId candidateId = CandidateId.of(request.candidateId());
        ElectionType electionType = ElectionType.fromString(request.electionType());
        City city = City.of(request.city() != null && !request.city().isBlank() ? request.city() : "Bogotá");
        Region region = City.resolveRegionFor(city.value(), request.region());
        String ipText = (request.clientIp() != null && !request.clientIp().isBlank())
            ? request.clientIp()
            : (resolvedClientIp != null && !resolvedClientIp.isBlank() ? resolvedClientIp : "127.0.0.1");
        IpAddress clientIp = IpAddress.of(ipText);
        VoteSource voteSource = VoteSource.fromString(request.voteSource());
        String voterName = (request.voterName() != null && !request.voterName().isBlank())
            ? request.voterName()
            : "Ciudadano C.C. " + voterId.value();

        // 3. Duplicate Cedula Protection (1 vote per cedula per election -> return existing ticket!)
        Optional<VoteTicketRecord> existingTicket = ticketRepository.findByVoterAndElection(voterId, electionType);
        if (existingTicket.isPresent()) {
            log.info("[{}] Duplicate vote prevented for cedula={} election={} -> returning ticket {}",
                nodeId, voterId, electionType, existingTicket.get().ticketCode());
            throw new DuplicateVoteException(existingTicket.get());
        }

        // 4. Local KTable Eligibility check
        boolean strictMode = stateRepository.isStrictCensusMode();
        boolean explicitlyRevoked = (eligibilityStore instanceof InMemoryEligibilityStore mem)
            && mem.isExplicitlyRevoked(voterId);

        if (explicitlyRevoked) {
            log.warn("[{}] Vote rejected: cedula '{}' is revoked/ineligible.", nodeId, voterId);
            throw new VoterNotEligibleException(voterId);
        }

        if (!eligibilityStore.isEligible(voterId)) {
            if (strictMode) {
                log.warn("[{}] Vote rejected under strict census mode: cedula '{}' not in KTable.", nodeId, voterId);
                throw new VoterNotEligibleException(voterId);
            }
            // Open Citizen Verification Mode: auto-register verified cedula into KTable & Kafka eligible_voters
            EligibleVoterRecord enrolled = new EligibleVoterRecord(voterId, voterName, region, Instant.now());
            eligibilityStore.registerEligible(enrolled);
            eligibleVoterPublisher.publishEligibleVoter(enrolled);
        }

        // 5. Generate Official Verifiable Digital Ticket & atomically reserve (voterId, electionType)
        Instant now = Instant.now();
        String ticketCode = VoteTicketCode.generate(electionType).value();
        String candidateName = CandidateCatalog.resolveDisplayName(candidateId.value());
        String verificationHash = VoteTicketCode.computeVerificationHash(ticketCode, voterId.value(), candidateId.value(), now);

        VoteTicketRecord ticket = new VoteTicketRecord(
            ticketCode,
            voterId.value(),
            voterName,
            city.value(),
            region,
            electionType,
            candidateId.value(),
            candidateName,
            voteSource,
            nodeId,
            verificationHash,
            now
        );

        boolean inserted = ticketRepository.saveIfAbsent(ticket);
        if (!inserted) {
            VoteTicketRecord concurrentTicket = ticketRepository.findByVoterAndElection(voterId, electionType)
                .orElse(ticket);
            throw new DuplicateVoteException(concurrentTicket);
        }

        // 6. Publish verified vote event to Kafka compacted topic raw_votes
        VoteRecord voteRecord = new VoteRecord(
            voterId,
            candidateId,
            region,
            clientIp,
            now,
            city.value(),
            electionType,
            voteSource,
            ticketCode,
            nodeId
        );
        votePublisher.publishRawVote(voteRecord);

        log.info("[{}] Vote ACCEPTED: cedula={} city={} region={} election={} candidate={} source={} ticket={}",
            nodeId, voterId, city, region, electionType, candidateId, voteSource, ticketCode);

        return ticket;
    }

    public List<VoteTicketRecord> lookupTicketsByCedula(String cedula) {
        return ticketRepository.findByVoter(VoterId.of(cedula));
    }

    public String getNodeId() {
        return nodeId;
    }

    public static VotingLifecycleState resolveLifecycleState(String stateName) {
        if (stateName == null) return HabilitadoState.INSTANCE;
        return switch (stateName.trim().toUpperCase()) {
            case "EN_AUDITORIA" -> EnAuditoriaState.INSTANCE;
            case "CERRADO" -> CerradoState.INSTANCE;
            default -> HabilitadoState.INSTANCE;
        };
    }
}
