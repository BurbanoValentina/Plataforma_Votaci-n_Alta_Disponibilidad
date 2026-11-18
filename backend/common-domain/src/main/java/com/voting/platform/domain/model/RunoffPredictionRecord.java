package com.voting.platform.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable Domain Record representing a statistical 2nd-Round (Segunda Vuelta) prediction.
 */
public record RunoffPredictionRecord(
    ElectionType electionType,
    String mode,
    long totalVotesAnalyzed,
    long totalRealVotes,
    long totalSimulatedVotes,
    boolean runoffRequired,
    String verdictTitle,
    String verdictSummary,
    double marginOfErrorPct,
    List<CandidateProjection> finalists,
    List<CandidateProjection> allRankings,
    Instant generatedAt
) {
    public record CandidateProjection(
        String candidateId,
        int ballotNumber,
        String name,
        String party,
        String accentColor,
        String photoUrl,
        long votes,
        long realVotes,
        long simulatedVotes,
        double currentSharePct,
        double projectedRunoffSharePct,
        double winProbabilityPct,
        boolean advancesToRunoff
    ) {}

    public RunoffPredictionRecord {
        Objects.requireNonNull(electionType, "electionType cannot be null");
        finalists = finalists == null ? List.of() : List.copyOf(finalists);
        allRankings = allRankings == null ? List.of() : List.copyOf(allRankings);
        if (generatedAt == null) generatedAt = Instant.now();
    }
}
