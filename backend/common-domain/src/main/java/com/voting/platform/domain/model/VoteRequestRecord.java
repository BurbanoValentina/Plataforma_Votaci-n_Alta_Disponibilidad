package com.voting.platform.domain.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Immutable DTO record for HTTP POST /api/vote (and /vote) request payloads with Bean Validation.
 */
public record VoteRequestRecord(
    @NotBlank(message = "La cédula (voterId) es obligatoria")
    @Size(min = 3, max = 64, message = "La cédula debe tener entre 3 y 64 caracteres")
    @Pattern(regexp = "^[a-zA-Z0-9_\\-]+$", message = "La cédula contiene caracteres inválidos")
    String voterId,

    String voterName,

    @NotBlank(message = "El candidato es obligatorio")
    @Size(min = 2, max = 64, message = "candidateId debe tener entre 2 y 64 caracteres")
    @Pattern(regexp = "^[a-zA-Z0-9_\\-]+$", message = "candidateId contiene caracteres inválidos")
    String candidateId,

    @NotBlank(message = "La ciudad es obligatoria")
    String city,

    String region,

    @NotBlank(message = "El tipo de votación (NACIONAL o REGIONAL) es obligatorio")
    String electionType,

    String clientIp,

    String voteSource
) {
    public VoteRequestRecord {
        if (voterId != null) voterId = voterId.trim();
        if (voterName != null) voterName = voterName.trim();
        if (candidateId != null) candidateId = candidateId.trim();
        if (city != null) city = city.trim();
        if (region != null) region = region.trim().toUpperCase();
        if (electionType != null) electionType = electionType.trim().toUpperCase();
        if (clientIp != null) clientIp = clientIp.trim();
        if (voteSource != null) voteSource = voteSource.trim().toUpperCase();
    }

    /**
     * Legacy 4-argument convenience constructor for backward compatibility in unit tests.
     */
    public VoteRequestRecord(String voterId, String candidateId, String region, String clientIp) {
        this(voterId, "Ciudadano Verificado", candidateId, "Bogotá", region, "NACIONAL", clientIp, "REAL");
    }
}
