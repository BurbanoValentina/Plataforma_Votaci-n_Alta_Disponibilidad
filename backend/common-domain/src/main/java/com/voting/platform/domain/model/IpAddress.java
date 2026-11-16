package com.voting.platform.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Inmutable Value Object representing a client IP address (IPv4 or IPv6 / localhost).
 * Enforces Fail-Fast validation in its compact constructor.
 */
public record IpAddress(String value) {

    private static final Pattern IPV4_PATTERN = Pattern.compile(
        "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    );

    private static final Pattern IPV6_PATTERN = Pattern.compile(
        "^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$|^::1$|^::$"
    );

    public IpAddress {
        Objects.requireNonNull(value, "IP address cannot be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("IP address cannot be blank");
        }
        if (!"localhost".equalsIgnoreCase(trimmed) &&
            !IPV4_PATTERN.matcher(trimmed).matches() &&
            !IPV6_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Invalid IP address format: " + trimmed);
        }
        value = trimmed;
    }

    public static IpAddress of(String value) {
        return new IpAddress(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
