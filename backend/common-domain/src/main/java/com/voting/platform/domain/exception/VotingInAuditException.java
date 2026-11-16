package com.voting.platform.domain.exception;

/**
 * Thrown when an attempt to cast a vote is made during the EN_AUDITORIA state.
 */
public class VotingInAuditException extends DomainException {
    public VotingInAuditException(String message) {
        super(message);
    }

    public static VotingInAuditException defaultMessage() {
        return new VotingInAuditException("Voting platform is currently in audit mode. Votes are locked.");
    }
}
