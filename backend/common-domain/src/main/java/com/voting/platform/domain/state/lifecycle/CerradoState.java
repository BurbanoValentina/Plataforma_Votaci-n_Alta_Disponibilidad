package com.voting.platform.domain.state.lifecycle;

import com.voting.platform.domain.exception.VotingClosedException;

/**
 * Concrete State: CERRADO.
 * Voting is closed and attempts to vote fail immediately.
 */
public final class CerradoState implements VotingLifecycleState {

    public static final CerradoState INSTANCE = new CerradoState();
    public static final String NAME = "CERRADO";

    private CerradoState() {}

    @Override
    public boolean isVotingAllowed() {
        return false;
    }

    @Override
    public void validateCanVote() {
        throw VotingClosedException.defaultMessage();
    }

    @Override
    public VotingLifecycleState transitionToNext() {
        return EnAuditoriaState.INSTANCE;
    }

    @Override
    public String getStateName() {
        return NAME;
    }
}
