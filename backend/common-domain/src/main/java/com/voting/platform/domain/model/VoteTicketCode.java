package com.voting.platform.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable Value Object representing an official verifiable Vote Ticket identifier.
 */
public record VoteTicketCode(String value) {

    public VoteTicketCode {
        Objects.requireNonNull(value, "VoteTicketCode cannot be null");
        String trimmed = value.trim().toUpperCase();
        if (trimmed.length() < 6 || trimmed.length() > 48) {
            throw new IllegalArgumentException("Invalid VoteTicketCode length: " + trimmed);
        }
        value = trimmed;
    }

    public static VoteTicketCode of(String value) {
        return new VoteTicketCode(value);
    }

    public static VoteTicketCode generate(ElectionType electionType) {
        ElectionType type = electionType != null ? electionType : ElectionType.NACIONAL;
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        return new VoteTicketCode("TKT-" + type.getTicketPrefix() + "-" + suffix);
    }

    public static String computeVerificationHash(String ticketCode, String voterId, String candidateId, Instant timestamp) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = ticketCode + "|" + voterId + "|" + candidateId + "|" + timestamp.toEpochMilli();
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16).toUpperCase();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
