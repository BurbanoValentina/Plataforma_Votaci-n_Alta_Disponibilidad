package com.voting.platform.domain.state.lifecycle;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Domain Aggregate Context representing the life cycle of a voting event.
 * Encapsulates state transitions without conditional branches.
 */
public class VotingSession {

    private final AtomicReference<VotingLifecycleState> currentState;

    public VotingSession() {
        this.currentState = new AtomicReference<>(HabilitadoState.INSTANCE);
    }

    public VotingSession(VotingLifecycleState initialState) {
        Objects.requireNonNull(initialState, "initialState cannot be null");
        this.currentState = new AtomicReference<>(initialState);
    }

    public VotingLifecycleState getCurrentState() {
        return this.currentState.get();
    }

    public String getCurrentStateName() {
        return this.currentState.get().getStateName();
    }

    public boolean isVotingAllowed() {
        return this.currentState.get().isVotingAllowed();
    }

    /**
     * Invariant check: Throws domain exception if vote is disallowed in current state.
     */
    public void ensureVotingAllowed() {
        this.currentState.get().validateCanVote();
    }

    /**
     * Advance to the next phase in the lifecycle.
     */
    public VotingLifecycleState advanceToNextPhase() {
        return this.currentState.updateAndGet(VotingLifecycleState::transitionToNext);
    }

    /**
     * Directly switch to a specific state (e.g. administrative command).
     */
    public void transitionTo(VotingLifecycleState newState) {
        Objects.requireNonNull(newState, "newState cannot be null");
        this.currentState.set(newState);
    }
}
