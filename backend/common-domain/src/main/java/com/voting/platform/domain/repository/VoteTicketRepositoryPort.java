package com.voting.platform.domain.repository;

import com.voting.platform.domain.model.ElectionType;
import com.voting.platform.domain.dto.response.VoteTicketRecord;
import com.voting.platform.domain.model.VoterId;

import java.util.List;
import com.voting.platform.domain.dto.response.VoteTicketRecord;
import java.util.Optional;

/**
 * Outbound Port (DIP) for atomic Vote Ticket persistence and duplicate cedula enforcement
 * across distributed load-balanced application nodes.
 */
public interface VoteTicketRepositoryPort {

    Optional<VoteTicketRecord> findByVoterAndElection(VoterId voterId, ElectionType electionType);

    List<VoteTicketRecord> findByVoter(VoterId voterId);

    /**
     * Atomically persists the ticket if no ticket exists for (voterId, electionType).
     *
     * @return true if saved as new ticket, false if a ticket already existed.
     */
    boolean saveIfAbsent(VoteTicketRecord ticket);

    List<VoteTicketRecord> findRecent(int limit);
}

