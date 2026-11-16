package com.voting.platform.domain.exception;

/**
 * Thrown when vote attributes fail domain invariant checks.
 */
public class InvalidVoteException extends DomainException {
    public InvalidVoteException(String message) {
        super(message);
    }
}
