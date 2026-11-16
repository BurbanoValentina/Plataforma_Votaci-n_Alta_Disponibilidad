package com.voting.platform.domain.state.lifecycle;

/**
 * Concrete State: HABILITADO.
 * Voting is fully active and accepting incoming votes.
 */
public final class HabilitadoState implements VotingLifecycleState {

    public static final HabilitadoState INSTANCE = new HabilitadoState();
    public static final String NAME = "HABILITADO";

    private HabilitadoState() {}

    @Override
    public boolean isVotingAllowed() {
        return true;
    }

    @Override
    public void validateCanVote() {
        // No-op: votes are accepted in HABILITADO state
    }

    @Override
    public VotingLifecycleState transitionToNext() {
        return CerradoState.INSTANCE;
    }

    @Override
    public String getStateName() {
        return NAME;
    }
}
