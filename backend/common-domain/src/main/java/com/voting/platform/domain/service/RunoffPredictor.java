package com.voting.platform.domain.service;

import com.voting.platform.domain.model.CandidateCatalog;
import com.voting.platform.domain.model.ElectionType;
import com.voting.platform.domain.dto.response.GlobalResultRecord;
import com.voting.platform.domain.dto.response.RunoffPredictionRecord;
import com.voting.platform.domain.dto.response.RunoffPredictionRecord.CandidateProjection;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Rich Domain Service that computes Second-Round (Segunda Vuelta) electoral predictions
 * using Colombian electoral rules (50% + 1 threshold) and 95% confidence intervals.
 * Supports analyzing ALL votes, REAL citizen votes only, or SIMULATED votes only.
 */
public final class RunoffPredictor {

    private RunoffPredictor() {}

    public static RunoffPredictionRecord predict(
        GlobalResultRecord global,
        ElectionType electionType,
        String mode
    ) {
        Objects.requireNonNull(global, "global cannot be null");
        ElectionType type = electionType != null ? electionType : ElectionType.NACIONAL;
        String resolvedMode = (mode == null || mode.isBlank()) ? "ALL" : mode.trim().toUpperCase();

        Map<String, Long> totals = type == ElectionType.NACIONAL
            ? global.nationalCandidateVotes() : global.regionalCandidateVotes();
        Map<String, Long> reals = type == ElectionType.NACIONAL
            ? global.nationalRealVotes() : global.regionalRealVotes();
        Map<String, Long> sims = type == ElectionType.NACIONAL
            ? global.nationalSimulatedVotes() : global.regionalSimulatedVotes();

        List<CandidateCatalog.CandidateProfile> catalog = CandidateCatalog.byElectionType(type);

        record RawStanding(CandidateCatalog.CandidateProfile profile, long activeVotes, long realVotes, long simVotes) {}

        List<RawStanding> standings = new ArrayList<>();
        long activeTotal = 0L;
        long totalReal = 0L;
        long totalSim = 0L;

        for (CandidateCatalog.CandidateProfile profile : catalog) {
            long r = reals.getOrDefault(profile.id(), 0L);
            long s = sims.getOrDefault(profile.id(), 0L);
            long t = totals.getOrDefault(profile.id(), r + s);
            long active = switch (resolvedMode) {
                case "REAL" -> r;
                case "SIMULATED" -> s;
                default -> t;
            };
            activeTotal += active;
            totalReal += r;
            totalSim += s;
            standings.add(new RawStanding(profile, active, r, s));
        }

        standings.sort(Comparator.comparingLong(RawStanding::activeVotes).reversed()
            .thenComparingInt(st -> st.profile().ballotNumber()));

        long top1Votes = standings.size() > 0 ? standings.get(0).activeVotes() : 0L;
        long top2Votes = standings.size() > 1 ? standings.get(1).activeVotes() : 0L;
        double top1Pct = activeTotal > 0 ? (top1Votes * 100.0) / activeTotal : 0.0;
        double top2Pct = activeTotal > 0 ? (top2Votes * 100.0) / activeTotal : 0.0;

        // 95% confidence interval margin of error: 1.96 * sqrt(p*(1-p)/n)
        double marginOfErrorPct = 0.0;
        if (activeTotal > 0) {
            double p = Math.min(0.99, Math.max(0.01, top1Pct / 100.0));
            marginOfErrorPct = round2(1.96 * Math.sqrt((p * (1.0 - p)) / activeTotal) * 100.0);
        }

        boolean runoffRequired = activeTotal == 0 || top1Pct <= 50.0;

        // Projected 2nd-round head-to-head share redistributing 3rd/4th place proportionally
        long finalistsBase = top1Votes + top2Votes;
        double proj1 = finalistsBase > 0 ? round2((top1Votes * 100.0) / finalistsBase) : 50.0;
        double proj2 = finalistsBase > 0 ? round2(100.0 - proj1) : 50.0;

        // Logistic win probability based on lead & sample size
        double zScore = 0.0;
        if (finalistsBase > 0) {
            double diff = (proj1 - proj2) / 100.0;
            double se = Math.max(0.005, Math.sqrt(0.25 / finalistsBase));
            zScore = Math.max(-6.0, Math.min(6.0, diff / se));
        }
        double winProb1 = round2(100.0 / (1.0 + Math.exp(-0.85 * zScore)));
        double winProb2 = round2(100.0 - winProb1);

        List<CandidateProjection> projections = new ArrayList<>();
        for (int i = 0; i < standings.size(); i++) {
            RawStanding st = standings.get(i);
            double share = activeTotal > 0 ? round2((st.activeVotes() * 100.0) / activeTotal) : 0.0;
            boolean advances = i < 2 && activeTotal > 0;
            double projShare = i == 0 ? proj1 : (i == 1 ? proj2 : 0.0);
            double winProb = i == 0 ? winProb1 : (i == 1 ? winProb2 : 0.0);

            projections.add(new CandidateProjection(
                st.profile().id(),
                st.profile().ballotNumber(),
                st.profile().name(),
                st.profile().party(),
                st.profile().accentColor(),
                st.profile().photoUrl(),
                st.activeVotes(),
                st.realVotes(),
                st.simVotes(),
                share,
                projShare,
                winProb,
                advances
            ));
        }

        List<CandidateProjection> finalists = projections.size() >= 2
            ? List.of(projections.get(0), projections.get(1))
            : projections;

        String verdictTitle;
        String verdictSummary;
        if (activeTotal == 0) {
            verdictTitle = "Esperando votos para calcular proyección";
            verdictSummary = "Aún no hay votos suficientes en el filtro seleccionado (" + resolvedMode + ").";
        } else if (!runoffRequired) {
            verdictTitle = "Victoria proyectada en Primera Vuelta: " + finalists.get(0).name();
            verdictSummary = String.format(
                "%s supera el umbral del 50%% + 1 con %.2f%% (±%.2f%%). Si la tendencia baja del 50%%, pasaría a segunda vuelta junto a %s (%.2f%%).",
                finalists.get(0).name(), top1Pct, marginOfErrorPct,
                finalists.size() > 1 ? finalists.get(1).name() : "N/A", top2Pct
            );
        } else {
            verdictTitle = String.format("Segunda Vuelta Proyectada: %s vs %s",
                finalists.get(0).name(), finalists.size() > 1 ? finalists.get(1).name() : "Por definir");
            verdictSummary = String.format(
                "Ningún candidato supera el 50%% + 1. %s (%.2f%%) y %s (%.2f%%) lideran el escrutinio y avanzarían a Segunda Vuelta (margen de error ±%.2f%%).",
                finalists.get(0).name(), top1Pct,
                finalists.size() > 1 ? finalists.get(1).name() : "N/A", top2Pct,
                marginOfErrorPct
            );
        }

        return new RunoffPredictionRecord(
            type,
            resolvedMode,
            activeTotal,
            totalReal,
            totalSim,
            runoffRequired,
            verdictTitle,
            verdictSummary,
            marginOfErrorPct,
            finalists,
            projections,
            Instant.now()
        );
    }

    private static double round2(double val) {
        return Math.round(val * 100.0) / 100.0;
    }
}

