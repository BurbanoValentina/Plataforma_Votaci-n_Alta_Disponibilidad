package com.voting.platform.domain.exception;

import com.voting.platform.domain.dto.response.VoteTicketRecord;

import java.util.Objects;

/**
 * Domain Exception thrown when a citizen attempts to vote more than once with the same cedula
 * in the same election. Carries the original official vote ticket so the frontend can display it.
 */
public class DuplicateVoteException extends DomainException {

    private final VoteTicketRecord existingTicket;

    public DuplicateVoteException(VoteTicketRecord existingTicket) {
        super(String.format(
            "La cédula %s ya registró su voto en %s (Ticket: %s). No es posible repetir el voto.",
            existingTicket.voterId(),
            existingTicket.electionType().getDisplayName(),
            existingTicket.ticketCode()
        ));
        this.existingTicket = Objects.requireNonNull(existingTicket, "existingTicket cannot be null");
    }

    public String getErrorCode() {
        return "DUPLICATE_VOTE";
    }

    public VoteTicketRecord getExistingTicket() {
        return existingTicket;
    }
}
