package com.voting.platform.domain.state.risk;

/**
 * Concrete State: BLOQUEADA risk level.
 * Extreme voting frequency detected (DDoS or botnet pattern). All votes from this IP are flagged.
 */
public final class BlockedRiskState implements IpRiskState {

    public static final BlockedRiskState INSTANCE = new BlockedRiskState();
    public static final String LEVEL = "BLOQUEADA";

    private BlockedRiskState() {}

    @Override
    public String getLevel() {
        return LEVEL;
    }

    @Override
    public boolean isBlocked() {
        return true;
    }

    @Override
    public boolean isSuspicious() {
        return true;
    }

    @Override
    public IpRiskState evaluateRate(int votesInWindow, int warningThreshold, int blockThreshold) {
        // Once blocked, requires cooldown (0 votes in window) to step down to SOSPECHOSA
        if (votesInWindow == 0) {
            return SuspiciousRiskState.INSTANCE;
        }
        return this;
    }
}
