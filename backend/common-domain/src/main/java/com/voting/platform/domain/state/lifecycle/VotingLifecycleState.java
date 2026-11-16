package com.voting.platform.domain.state.lifecycle;

/**
 * State Pattern Interface modeling the life cycle of a voting session
 * (HABILITADO, CERRADO, EN_AUDITORIA) without nested conditionals.
 */
public interface VotingLifecycleState {

    /**
     * Whether votes can currently be accepted.
     */
    boolean isVotingAllowed();

    /**
     * Fail-Fast validation. Throws domain exception if current state rejects votes.
     */
    void validateCanVote();

    /**
     * Advances to the next sequential phase in the voting lifecycle.
     */
    VotingLifecycleState transitionToNext();

    /**
     * String identifier of the state.
     */
    String getStateName();
}
