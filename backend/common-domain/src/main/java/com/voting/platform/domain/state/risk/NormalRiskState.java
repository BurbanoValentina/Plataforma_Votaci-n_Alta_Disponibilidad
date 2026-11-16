package com.voting.platform.domain.state.risk;

/**
 * Concrete State: NORMAL risk level.
 * Standard voting behavior, no anomaly detected.
 */
public final class NormalRiskState implements IpRiskState {

    public static final NormalRiskState INSTANCE = new NormalRiskState();
    public static final String LEVEL = "NORMAL";

    private NormalRiskState() {}

    @Override
    public String getLevel() {
        return LEVEL;
    }

    @Override
    public boolean isBlocked() {
        return false;
    }

    @Override
    public boolean isSuspicious() {
        return false;
    }

    @Override
    public IpRiskState evaluateRate(int votesInWindow, int warningThreshold, int blockThreshold) {
        if (votesInWindow >= blockThreshold) {
            return BlockedRiskState.INSTANCE;
        } else if (votesInWindow >= warningThreshold) {
            return SuspiciousRiskState.INSTANCE;
        }
        return this;
    }
}
