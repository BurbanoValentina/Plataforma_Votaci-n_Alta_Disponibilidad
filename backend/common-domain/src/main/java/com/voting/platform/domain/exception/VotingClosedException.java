package com.voting.platform.domain.exception;

/**
 * Thrown when an attempt to cast a vote is made during the CERRADO state.
 */
public class VotingClosedException extends DomainException {
    public VotingClosedException(String message) {
        super(message);
    }

    public static VotingClosedException defaultMessage() {
        return new VotingClosedException("Voting has officially closed. No more votes are accepted.");
    }
}
