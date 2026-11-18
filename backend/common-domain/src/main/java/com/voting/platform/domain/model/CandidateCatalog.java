package com.voting.platform.domain.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Canonical catalog of candidates for National and Regional elections,
 * including verified portrait photography and institutional branding.
 */
public final class CandidateCatalog {

    public record CandidateProfile(
        String id,
        int ballotNumber,
        String name,
        String party,
        ElectionType electionType,
        String slogan,
        String accentColor,
        String photoUrl
    ) {}

    private static final List<CandidateProfile> ALL_CANDIDATES = List.of(
        // Eleccion Nacional 2026
        new CandidateProfile(
            "cand_nac_01", 1, "Elena Vargas", "Alianza Progreso Nacional",
            ElectionType.NACIONAL, "Infraestructura digital y educación pública", "#20B2A6",
            "https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?auto=format&fit=crop&w=240&h=240&q=80"
        ),
        new CandidateProfile(
            "cand_nac_02", 2, "Mateo Restrepo", "Movimiento Futuro Colombia",
            ElectionType.NACIONAL, "Innovación tecnológica y empleo joven", "#0f766e",
            "https://images.unsplash.com/photo-1560250097-0b93528c311a?auto=format&fit=crop&w=240&h=240&q=80"
        ),
        new CandidateProfile(
            "cand_nac_03", 3, "Camila Mendoza", "Coalición Verde Ciudadana",
            ElectionType.NACIONAL, "Transición energética y sostenibilidad", "#0284c7",
            "https://images.unsplash.com/photo-1580489944761-15a19d654956?auto=format&fit=crop&w=240&h=240&q=80"
        ),
        new CandidateProfile(
            "cand_nac_04", 4, "Andrés Salazar", "Partido Renovación Democrática",
            ElectionType.NACIONAL, "Seguridad institucional y transparencia", "#b45309",
            "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=240&h=240&q=80"
        ),
        // Eleccion Regional 2026
        new CandidateProfile(
            "cand_reg_01", 11, "Sofía Herrera", "Coalición Territorial Unida",
            ElectionType.REGIONAL, "Conectividad vial y salud regional", "#20B2A6",
            "https://images.unsplash.com/photo-1551836022-d5d88e9218df?auto=format&fit=crop&w=240&h=240&q=80"
        ),
        new CandidateProfile(
            "cand_reg_02", 12, "Diego Morales", "Frente de Desarrollo Regional",
            ElectionType.REGIONAL, "Agroindustria y competitividad local", "#0369a1",
            "https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?auto=format&fit=crop&w=240&h=240&q=80"
        ),
        new CandidateProfile(
            "cand_reg_03", 13, "Valentina Ríos", "Pacto Ciudadano de Regiones",
            ElectionType.REGIONAL, "Universidades regionales y cultura", "#7c3aed",
            "https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=240&h=240&q=80"
        ),
        new CandidateProfile(
            "cand_reg_04", 14, "Sebastián Castro", "Alianza Descentralización Viva",
            ElectionType.REGIONAL, "Autonomía fiscal y turismo sostenible", "#be123c",
            "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=240&h=240&q=80"
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
