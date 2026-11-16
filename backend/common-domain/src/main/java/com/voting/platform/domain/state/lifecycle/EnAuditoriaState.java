package com.voting.platform.domain.state.lifecycle;

import com.voting.platform.domain.exception.VotingInAuditException;

/**
 * Concrete State: EN_AUDITORIA.
 * Ballots are locked for algorithmic and forensic audit.
 */
public final class EnAuditoriaState implements VotingLifecycleState {

    public static final EnAuditoriaState INSTANCE = new EnAuditoriaState();
    public static final String NAME = "EN_AUDITORIA";

    private EnAuditoriaState() {}

    @Override
    public boolean isVotingAllowed() {
        return false;
    }

    @Override
    public void validateCanVote() {
        throw VotingInAuditException.defaultMessage();
    }

    @Override
    public VotingLifecycleState transitionToNext() {
        return HabilitadoState.INSTANCE;
    }

    @Override
    public String getStateName() {
        return NAME;
    }
}
