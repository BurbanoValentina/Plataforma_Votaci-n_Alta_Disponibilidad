package com.voting.platform.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Inmutable Value Object representing a unique Voter Identifier.
 * Enforces Fail-Fast validation in its compact constructor.
 */
public record VoterId(String value) {

    private static final Pattern VALID_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\-]{3,64}$");

    public VoterId {
        Objects.requireNonNull(value, "Voter ID cannot be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Voter ID cannot be blank");
        }
        if (!VALID_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(
                "Voter ID must be 3-64 alphanumeric characters, underscores or hyphens: " + trimmed
            );
        }
        value = trimmed;
    }

    public static VoterId of(String value) {
        return new VoterId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
