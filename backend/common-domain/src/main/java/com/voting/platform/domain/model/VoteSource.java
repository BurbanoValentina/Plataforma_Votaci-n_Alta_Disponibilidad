package com.voting.platform.domain.model;

/**
 * Domain Enum differentiating real citizen votes from automated real-time simulator votes.
 */
public enum VoteSource {
    REAL,
    SIMULATED;

    public static VoteSource fromString(String value) {
        if (value == null || value.isBlank()) {
            return REAL;
        }
        String clean = value.trim().toUpperCase();
        if ("SIMULATED".equals(clean) || "SIMULADO".equals(clean)) {
            return SIMULATED;
        }
        return REAL;
    }
}
