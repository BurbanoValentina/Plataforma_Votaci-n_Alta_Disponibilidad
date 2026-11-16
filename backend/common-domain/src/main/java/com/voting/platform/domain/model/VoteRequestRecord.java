package com.voting.platform.domain.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Immutable DTO record for HTTP POST /vote request payloads with Bean Validation.
 */
public record VoteRequestRecord(
    @NotBlank(message = "voterId is required")
    @Size(min = 3, max = 64, message = "voterId must be between 3 and 64 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_\\-]+$", message = "voterId contains invalid characters")
    String voterId,

    @NotBlank(message = "candidateId is required")
    @Size(min = 2, max = 64, message = "candidateId must be between 2 and 64 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_\\-]+$", message = "candidateId contains invalid characters")
    String candidateId,

    @NotBlank(message = "region is required")
    String region,

    @NotBlank(message = "clientIp is required")
    String clientIp
) {
    public VoteRequestRecord {
        if (voterId != null) voterId = voterId.trim();
        if (candidateId != null) candidateId = candidateId.trim();
        if (region != null) region = region.trim().toUpperCase();
        if (clientIp != null) clientIp = clientIp.trim();
    }
}
