package com.voting.platform.domain.model;

import java.util.Arrays;

/**
 * Domain Enum representing geographical regions for voting and aggregation.
 */
public enum Region {
    NORTE("norte"),
    SUR("sur"),
    ESTE("este"),
    OESTE("oeste"),
    CENTRO("centro");

    private final String routingKeySuffix;

    Region(String routingKeySuffix) {
        this.routingKeySuffix = routingKeySuffix;
    }

    public String getRoutingKeySuffix() {
        return routingKeySuffix;
    }

    public String toRoutingKey() {
        return "results." + routingKeySuffix;
    }

    public static Region fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Region value cannot be null or blank");
        }
        String clean = value.trim().toUpperCase();
        for (Region r : values()) {
            if (r.name().equals(clean) || r.routingKeySuffix.equalsIgnoreCase(clean)) {
                return r;
            }
        }
        throw new IllegalArgumentException(
            "Unknown region: '" + value + "'. Valid regions are: " + Arrays.toString(values())
        );
    }
}
