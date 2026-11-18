package com.voting.platform.domain.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Canonical catalog of candidates for National and Regional elections.
 */
public final class CandidateCatalog {

    public record CandidateProfile(
        String id,
        int ballotNumber,
        String name,
        String party,
        ElectionType electionType,
        String slogan,
        String accentColor
    ) {}

    private static final List<CandidateProfile> ALL_CANDIDATES = List.of(
        // Eleccion Nacional 2026
        new CandidateProfile(
            "cand_nac_01", 1, "Elena Vargas", "Alianza Progreso Nacional",
            ElectionType.NACIONAL, "Infraestructura digital y educación pública", "#1d4ed8"
        ),
        new CandidateProfile(
            "cand_nac_02", 2, "Mateo Restrepo", "Movimiento Futuro Colombia",
            ElectionType.NACIONAL, "Innovación tecnológica y empleo joven", "#047857"
        ),
        new CandidateProfile(
            "cand_nac_03", 3, "Camila Mendoza", "Coalición Verde Ciudadana",
            ElectionType.NACIONAL, "Transición energética y sostenibilidad", "#0f766e"
        ),
        new CandidateProfile(
            "cand_nac_04", 4, "Andrés Salazar", "Partido Renovación Democrática",
            ElectionType.NACIONAL, "Seguridad institucional y transparencia", "#b45309"
        ),
        // Eleccion Regional 2026
        new CandidateProfile(
            "cand_reg_01", 11, "Sofía Herrera", "Coalición Territorial Unida",
            ElectionType.REGIONAL, "Conectividad vial y salud regional", "#4338ca"
        ),
        new CandidateProfile(
            "cand_reg_02", 12, "Diego Morales", "Frente de Desarrollo Regional",
            ElectionType.REGIONAL, "Agroindustria y competitividad local", "#0369a1"
        ),
        new CandidateProfile(
            "cand_reg_03", 13, "Valentina Ríos", "Pacto Ciudadano de Regiones",
            ElectionType.REGIONAL, "Universidades regionales y cultura", "#7c3aed"
        ),
        new CandidateProfile(
            "cand_reg_04", 14, "Sebastián Castro", "Alianza Descentralización Viva",
            ElectionType.REGIONAL, "Autonomía fiscal y turismo sostenible", "#be123c"
        )
    );

    private static final Map<String, CandidateProfile> BY_ID = ALL_CANDIDATES.stream()
        .collect(Collectors.toUnmodifiableMap(CandidateProfile::id, Function.identity()));

    private CandidateCatalog() {}

    public static List<CandidateProfile> all() {
        return ALL_CANDIDATES;
    }

    public static List<CandidateProfile> byElectionType(ElectionType type) {
        return ALL_CANDIDATES.stream()
            .filter(c -> c.electionType() == type)
            .toList();
    }

    public static Optional<CandidateProfile> findById(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(BY_ID.get(id.trim()));
    }

    public static String resolveDisplayName(String candidateId) {
        return findById(candidateId).map(CandidateProfile::name).orElse(candidateId);
    }
}
