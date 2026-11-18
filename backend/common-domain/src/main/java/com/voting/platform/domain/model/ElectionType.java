package com.voting.platform.domain.model;

import java.util.Arrays;

/**
 * Domain Enum representing the two active electoral processes supported by the platform.
 */
public enum ElectionType {
    NACIONAL("Elección Nacional 2026", "NAC"),
    REGIONAL("Elección Regional 2026", "REG");

    private final String displayName;
    private final String ticketPrefix;

    ElectionType(String displayName, String ticketPrefix) {
        this.displayName = displayName;
        this.ticketPrefix = ticketPrefix;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getTicketPrefix() {
        return ticketPrefix;
    }

    public static ElectionType fromString(String value) {
        if (value == null || value.isBlank()) {
            return NACIONAL;
        }
        String clean = value.trim().toUpperCase();
        for (ElectionType t : values()) {
            if (t.name().equals(clean)) {
                return t;
            }
        }
        throw new IllegalArgumentException(
            "Unknown election type: '" + value + "'. Valid values: " + Arrays.toString(values())
        );
    }
}
