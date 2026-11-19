package com.voting.platform.stream.repository;

import com.voting.platform.domain.model.CandidateCatalog;
import com.voting.platform.domain.model.EligibleVoterRecord;
import com.voting.platform.domain.model.Region;
import com.voting.platform.domain.model.SecurityAlertRecord;
import com.voting.platform.domain.model.VoteRecord;
import com.voting.platform.domain.model.VoterId;
import com.voting.platform.domain.repository.SecurityIncidentRepositoryPort;
import com.voting.platform.domain.repository.VoteAuditRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Transactional PostgreSQL Repository implementing DIP ports for Vote Audit & Security Incidents,
 * plus shared election_control polling for the distributed simulator.
 */
@Repository
public class PostgresStreamRepository implements VoteAuditRepositoryPort, SecurityIncidentRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(PostgresStreamRepository.class);

    public record SimulatorControl(
        String lifecycleState,
        boolean simulatorEnabled,
        int simulatorRateMs,
        int simulatorBatchSize,
        boolean botAttackRequested
    ) {}

    private final JdbcTemplate jdbcTemplate;

    public PostgresStreamRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate cannot be null");
    }

    @Override
    @Transactional
    public void saveAudit(VoteRecord vote) {
        Objects.requireNonNull(vote, "vote cannot be null");
        String candidateName = CandidateCatalog.resolveDisplayName(vote.candidateId().value());
        jdbcTemplate.update(
            """
            INSERT INTO audit_votes
                (ticket_code, voter_id, candidate_id, candidate_name, city, region,
                 election_type, vote_source, handled_by_node, ip_address, voted_at, archived_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            vote.ticketCode(),
            vote.voterId().value(),
            vote.candidateId().value(),
            candidateName,
            vote.city(),
            vote.region().name(),
            vote.electionType().name(),
            vote.voteSource().name(),
            vote.handledByNode(),
            vote.ipAddress().value(),
            Timestamp.from(vote.timestamp()),
            Timestamp.from(Instant.now())
        );
    }

    @Override
    public long count() {
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_votes", Long.class);
        return total != null ? total : 0L;
    }

    @Override
    @Transactional
    public void saveIncident(SecurityAlertRecord alert) {
        Objects.requireNonNull(alert, "alert cannot be null");
        jdbcTemplate.update(
            """
            INSERT INTO security_incidents (id, ip_address, risk_level, reason, vote_count, detected_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO NOTHING
            """,
            alert.alertId(),
            alert.ipAddress().value(),
            alert.riskLevel(),
            alert.reason(),
            alert.voteCountInWindow(),
            Timestamp.from(alert.timestamp())
        );
    }

    public List<EligibleVoterRecord> findAllEligibleVoters() {
        try {
            return jdbcTemplate.query(
                "SELECT voter_id, full_name, region, registered_at FROM registered_voters WHERE eligible = TRUE",
                (rs, rowNum) -> new EligibleVoterRecord(
                    VoterId.of(rs.getString("voter_id")),
                    rs.getString("full_name"),
                    Region.fromString(rs.getString("region")),
                    rs.getTimestamp("registered_at") != null ? rs.getTimestamp("registered_at").toInstant() : Instant.now()
                )
            );
        } catch (Exception e) {
            log.warn("Could not query registered_voters from PostgreSQL: {}", e.getMessage());
            return List.of();
        }
    }

    public List<VoteRecord> loadHistoricalVotes() {
        try {
            return jdbcTemplate.query(
                """
                SELECT ticket_code, voter_id, candidate_id, city, region, election_type,
                       vote_source, handled_by_node, ip_address, voted_at
                FROM audit_votes
                ORDER BY id ASC
                """,
                (rs, rowNum) -> VoteRecord.create(
                    rs.getString("voter_id"),
                    rs.getString("candidate_id"),
                    rs.getString("city"),
                    rs.getString("region"),
                    rs.getString("election_type"),
                    rs.getString("vote_source"),
                    rs.getString("ip_address"),
                    rs.getString("ticket_code"),
                    rs.getString("handled_by_node"),
                    rs.getTimestamp("voted_at") != null ? rs.getTimestamp("voted_at").toInstant() : Instant.now()
                )
            );
        } catch (Exception e) {
            log.warn("Could not hydrate historical votes from PostgreSQL: {}", e.getMessage());
            return List.of();
        }
    }

    public SimulatorControl readSimulatorControl() {
        try {
            List<SimulatorControl> list = jdbcTemplate.query(
                """
                SELECT lifecycle_state, simulator_enabled, simulator_rate_ms, simulator_batch_size, bot_attack_requested
                FROM election_control WHERE id = 1
                """,
                (rs, rowNum) -> new SimulatorControl(
                    rs.getString("lifecycle_state"),
                    rs.getBoolean("simulator_enabled"),
                    rs.getInt("simulator_rate_ms"),
                    rs.getInt("simulator_batch_size"),
                    rs.getBoolean("bot_attack_requested")
                )
            );
            return list.isEmpty()
                ? new SimulatorControl("HABILITADO", true, 1800, 2, false)
                : list.get(0);
        } catch (Exception e) {
            return new SimulatorControl("HABILITADO", true, 1800, 2, false);
        }
    }

    public void clearBotAttackFlag() {
        try {
            jdbcTemplate.update("UPDATE election_control SET bot_attack_requested = FALSE WHERE id = 1");
        } catch (Exception e) {
            log.warn("Failed clearing bot_attack_requested flag: {}", e.getMessage());
        }
    }

    public void recordNodeSimulatedVote(String nodeId) {
        try {
            jdbcTemplate.update(
                """
                INSERT INTO node_telemetry (node_id, availability_zone, requests_handled, votes_processed, last_heartbeat)
                VALUES (?, 'us-east-1', 1, 1, CURRENT_TIMESTAMP)
                ON CONFLICT (node_id) DO UPDATE
                SET votes_processed = node_telemetry.votes_processed + 1,
                    last_heartbeat = CURRENT_TIMESTAMP
                """,
                nodeId
            );
        } catch (Exception ignored) {
        }
    }
}
