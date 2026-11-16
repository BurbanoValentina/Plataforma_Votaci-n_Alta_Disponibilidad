package com.voting.platform.domain.state.risk;

/**
 * State Pattern Interface for IP Risk assessment (NORMAL, SOSPECHOSA, BLOQUEADA).
 */
public interface IpRiskState {

    /**
     * Textual risk level identifier.
     */
    String getLevel();

    /**
     * Returns true if requests from this IP should be actively rejected.
     */
    boolean isBlocked();

    /**
     * Returns true if this IP is under close scrutiny.
     */
    boolean isSuspicious();

    /**
     * Evaluates current vote rate against thresholds and returns the next risk state.
     */
    IpRiskState evaluateRate(int votesInWindow, int warningThreshold, int blockThreshold);
}
