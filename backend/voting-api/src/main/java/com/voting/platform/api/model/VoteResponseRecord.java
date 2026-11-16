package com.voting.platform.api.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable API response record returned by POST /vote.
 */
public record VoteResponseRecord(
    String status,
    String voterId,
    String message,
    Instant timestamp
) {
    public VoteResponseRecord {
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(voterId, "voterId cannot be null");
        Objects.requireNonNull(message, "message cannot be null");
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }

    public static VoteResponseRecord accepted(String voterId) {
        return new VoteResponseRecord("ACCEPTED", voterId, "Vote successfully validated and queued.", Instant.now());
    }

    public static VoteResponseRecord rejected(String voterId, String reason) {
        return new VoteResponseRecord("REJECTED", voterId, reason, Instant.now());
    }
}
