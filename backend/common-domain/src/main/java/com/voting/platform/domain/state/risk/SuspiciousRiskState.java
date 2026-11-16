package com.voting.platform.domain.state.risk;

/**
 * Concrete State: SOSPECHOSA risk level.
 * High frequency of votes detected from this IP.
 */
public final class SuspiciousRiskState implements IpRiskState {

    public static final SuspiciousRiskState INSTANCE = new SuspiciousRiskState();
    public static final String LEVEL = "SOSPECHOSA";

    private SuspiciousRiskState() {}

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
        return true;
    }

    @Override
    public IpRiskState evaluateRate(int votesInWindow, int warningThreshold, int blockThreshold) {
        if (votesInWindow >= blockThreshold) {
            return BlockedRiskState.INSTANCE;
        } else if (votesInWindow < warningThreshold) {
            return NormalRiskState.INSTANCE;
        }
        return this;
    }
}
