package com.voting.platform.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Inmutable Value Object representing a unique Candidate Identifier.
 * Enforces Fail-Fast validation in its compact constructor.
 */
public record CandidateId(String value) {

    private static final Pattern VALID_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\-]{2,64}$");

    public CandidateId {
        Objects.requireNonNull(value, "Candidate ID cannot be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Candidate ID cannot be blank");
        }
        if (!VALID_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(
                "Candidate ID must be 2-64 alphanumeric characters, underscores or hyphens: " + trimmed
            );
        }
        value = trimmed;
    }

    public static CandidateId of(String value) {
        return new CandidateId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
