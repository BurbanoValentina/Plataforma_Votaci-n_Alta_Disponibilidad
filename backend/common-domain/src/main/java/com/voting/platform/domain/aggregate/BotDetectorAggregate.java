package com.voting.platform.domain.aggregate;

import com.voting.platform.domain.model.IpAddress;
import com.voting.platform.domain.model.SecurityAlertRecord;
import com.voting.platform.domain.model.VoteRecord;
import com.voting.platform.domain.state.risk.IpTracker;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rich Domain Aggregate for Bot & Fraud Detection.
 * Manages per-IP activity monitors, detects anomalous spikes, and issues security alerts.
 */
public class BotDetectorAggregate {

    private final ConcurrentHashMap<IpAddress, IpTracker> ipTrackers;
    private final Duration windowDuration;
    private final int warningThreshold;
    private final int blockThreshold;

    public BotDetectorAggregate(Duration windowDuration, int warningThreshold, int blockThreshold) {
        if (warningThreshold <= 0 || blockThreshold <= warningThreshold) {
            throw new IllegalArgumentException("Thresholds must satisfy: 0 < warningThreshold < blockThreshold");
        }
        this.windowDuration = Objects.requireNonNull(windowDuration, "windowDuration cannot be null");
        this.warningThreshold = warningThreshold;
        this.blockThreshold = blockThreshold;
        this.ipTrackers = new ConcurrentHashMap<>();
    }

    public BotDetectorAggregate() {
        this(Duration.ofSeconds(10), 5, 15);
    }

    /**
     * Processes a vote from the given IP, evaluates risk state, and returns an alert if triggered.
     */
    public Optional<SecurityAlertRecord> inspectVote(VoteRecord vote) {
        Objects.requireNonNull(vote, "vote cannot be null");
        IpAddress ip = vote.ipAddress();

        IpTracker tracker = ipTrackers.computeIfAbsent(
            ip,
            k -> new IpTracker(k, windowDuration, warningThreshold, blockThreshold)
        );

        IpTracker.EvaluationResult result = tracker.recordVote(vote.timestamp());

        if (result.requiresAlert()) {
            String reason = String.format(
                "Anomalous voting activity: %d votes detected within %d seconds. Transitioned from %s to %s.",
                result.currentRate(),
                windowDuration.toSeconds(),
                result.previousState().getLevel(),
                result.newState().getLevel()
            );

            SecurityAlertRecord alert = SecurityAlertRecord.of(
                ip,
                result.newState().getLevel(),
                reason,
                result.currentRate()
            );
            return Optional.of(alert);
        }

        return Optional.empty();
    }

    public boolean isIpBlocked(IpAddress ipAddress) {
        IpTracker tracker = ipTrackers.get(ipAddress);
        return tracker != null && tracker.isBlocked();
    }

    public int getTrackedIpCount() {
        return ipTrackers.size();
    }
}
