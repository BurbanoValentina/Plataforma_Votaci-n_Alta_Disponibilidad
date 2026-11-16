package com.voting.platform.domain.exception;

import com.voting.platform.domain.model.VoterId;

/**
 * Thrown when an incoming vote originates from an unregistered or ineligible voter.
 * Results in HTTP 403 Forbidden.
 */
public class VoterNotEligibleException extends DomainException {

    private final String voterId;

    public VoterNotEligibleException(VoterId voterId) {
        super(String.format("Voter with ID '%s' is not registered as eligible to vote.", voterId.value()));
        this.voterId = voterId.value();
    }

    public VoterNotEligibleException(String voterId) {
        super(String.format("Voter with ID '%s' is not registered as eligible to vote.", voterId));
        this.voterId = voterId;
    }

    public String getVoterId() {
        return voterId;
    }
}
