package com.voting.platform.domain.state.risk;

import com.voting.platform.domain.model.IpAddress;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Domain Aggregate Context tracking voting activity for a specific IP Address.
 * Manages sliding time-window vote rate and State Pattern transitions.
 */
public class IpTracker {

    private final IpAddress ipAddress;
    private final Duration windowDuration;
    private final int warningThreshold;
    private final int blockThreshold;
    private final ConcurrentLinkedDeque<Instant> voteTimestamps;
    private final AtomicReference<IpRiskState> currentState;

    public record EvaluationResult(
        IpAddress ipAddress,
        IpRiskState previousState,
        IpRiskState newState,
        boolean stateChanged,
        int currentRate,
        Instant evaluationTime
    ) {
        public boolean requiresAlert() {
            return stateChanged && (newState.isSuspicious() || newState.isBlocked());
        }
    }

    public IpTracker(IpAddress ipAddress, Duration windowDuration, int warningThreshold, int blockThreshold) {
        Objects.requireNonNull(ipAddress, "ipAddress cannot be null");
        Objects.requireNonNull(windowDuration, "windowDuration cannot be null");
        if (warningThreshold <= 0 || blockThreshold <= warningThreshold) {
            throw new IllegalArgumentException("Thresholds must satisfy: 0 < warningThreshold < blockThreshold");
        }
        this.ipAddress = ipAddress;
        this.windowDuration = windowDuration;
        this.warningThreshold = warningThreshold;
        this.blockThreshold = blockThreshold;
        this.voteTimestamps = new ConcurrentLinkedDeque<>();
        this.currentState = new AtomicReference<>(NormalRiskState.INSTANCE);
    }

    public IpTracker(IpAddress ipAddress) {
        this(ipAddress, Duration.ofSeconds(10), 5, 15);
    }

    public IpAddress getIpAddress() {
        return ipAddress;
    }

    public IpRiskState getCurrentState() {
        return currentState.get();
    }

    public String getCurrentLevel() {
        return currentState.get().getLevel();
    }

    public boolean isBlocked() {
        return currentState.get().isBlocked();
    }

    /**
     * Records a vote timestamp, purges expired events, evaluates rate and transitions state.
     */
    public synchronized EvaluationResult recordVote(Instant timestamp) {
        Instant now = (timestamp != null) ? timestamp : Instant.now();
        voteTimestamps.addLast(now);

        // Purge votes outside the sliding window
        Instant cutoff = now.minus(windowDuration);
        while (!voteTimestamps.isEmpty() && voteTimestamps.peekFirst().isBefore(cutoff)) {
            voteTimestamps.pollFirst();
        }

        int currentRate = voteTimestamps.size();
        IpRiskState prev = currentState.get();
        IpRiskState next = prev.evaluateRate(currentRate, warningThreshold, blockThreshold);

        boolean changed = !prev.equals(next);
        if (changed) {
            currentState.set(next);
        }

        return new EvaluationResult(ipAddress, prev, next, changed, currentRate, now);
    }

    /**
     * Cleans up expired votes without adding a new vote. Used for cooldown evaluation.
     */
    public synchronized EvaluationResult evaluateCooldown(Instant now) {
        Instant cutoff = now.minus(windowDuration);
        while (!voteTimestamps.isEmpty() && voteTimestamps.peekFirst().isBefore(cutoff)) {
            voteTimestamps.pollFirst();
        }

        int currentRate = voteTimestamps.size();
        IpRiskState prev = currentState.get();
        IpRiskState next = prev.evaluateRate(currentRate, warningThreshold, blockThreshold);

        boolean changed = !prev.equals(next);
        if (changed) {
            currentState.set(next);
        }

        return new EvaluationResult(ipAddress, prev, next, changed, currentRate, now);
    }
}
