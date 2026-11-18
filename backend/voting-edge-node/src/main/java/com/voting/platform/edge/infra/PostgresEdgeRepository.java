package com.voting.platform.edge.infra;

import com.voting.platform.domain.model.ElectionType;
import com.voting.platform.domain.model.Region;
import com.voting.platform.domain.model.VoteSource;
import com.voting.platform.domain.model.VoteTicketRecord;
import com.voting.platform.domain.model.VoterId;
import com.voting.platform.domain.port.ElectionStateRepositoryPort;
import com.voting.platform.domain.port.VoteTicketRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Shared PostgreSQL Repository for distributed Edge Nodes:
 * - Atomic Vote Ticket storage (prevents duplicate cedula votes across AWS ALB nodes!)
 * - Shared Election State (GoF State Pattern: HABILITADO / EN_AUDITORIA / CERRADO)
 * - Complete Admin Console queries (audit_votes, registered_voters, security_incidents, node_telemetry)
 */
@Repository
public class PostgresEdgeRepository implements VoteTicketRepositoryPort, ElectionStateRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(PostgresEdgeRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final String nodeId;
    private final String availabilityZone;

    public PostgresEdgeRepository(
        JdbcTemplate jdbcTemplate,
        @Value("${voting.node-id:voting-edge-1}") String nodeId,
        @Value("${voting.availability-zone:us-east-1a}") String availabilityZone
    ) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate cannot be null");
        this.nodeId = nodeId;
        this.availabilityZone = availabilityZone;
    }

    @Scheduled(fixedRate = 10000)
    public void sendNodeHeartbeat() {
        try {
            jdbcTemplate.update(
                """
                INSERT INTO node_telemetry (node_id, availability_zone, requests_handled, votes_processed, last_heartbeat)
                VALUES (?, ?, 0, 0, CURRENT_TIMESTAMP)
                ON CONFLICT (node_id) DO UPDATE
                SET availability_zone = EXCLUDED.availability_zone,
                    last_heartbeat = CURRENT_TIMESTAMP
                """,
                nodeId, availabilityZone
            );
        } catch (Exception ignored) {
        }
    }

    public void incrementNodeRequests(boolean isVote) {
        try {
            jdbcTemplate.update(
                """
                INSERT INTO node_telemetry (node_id, availability_zone, requests_handled, votes_processed, last_heartbeat)
                VALUES (?, ?, 1, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (node_id) DO UPDATE
                SET requests_handled = node_telemetry.requests_handled + 1,
                    votes_processed = node_telemetry.votes_processed + ?,
                    last_heartbeat = CURRENT_TIMESTAMP
                """,
                nodeId, availabilityZone, isVote ? 1 : 0, isVote ? 1 : 0
            );
        } catch (Exception ignored) {
        }
    }

    @Override
    public Optional<VoteTicketRecord> findByVoterAndElection(VoterId voterId, ElectionType electionType) {
        List<VoteTicketRecord> rows = jdbcTemplate.query(
            """
            SELECT ticket_code, voter_id, voter_name, city, region, election_type,
                   candidate_id, candidate_name, vote_source, handled_by_node, verification_hash, voted_at
            FROM vote_tickets
            WHERE voter_id = ? AND election_type = ?
            """,
            (rs, rowNum) -> new VoteTicketRecord(
                rs.getString("ticket_code"),
                rs.getString("voter_id"),
                rs.getString("voter_name"),
                rs.getString("city"),
                Region.fromString(rs.getString("region")),
                ElectionType.fromString(rs.getString("election_type")),
                rs.getString("candidate_id"),
                rs.getString("candidate_name"),
                VoteSource.fromString(rs.getString("vote_source")),
                rs.getString("handled_by_node"),
                rs.getString("verification_hash"),
                rs.getTimestamp("voted_at").toInstant()
            ),
            voterId.value(), electionType.name()
        );
        return rows.stream().findFirst();
    }

    @Override
    public List<VoteTicketRecord> findByVoter(VoterId voterId) {
        return jdbcTemplate.query(
            """
            SELECT ticket_code, voter_id, voter_name, city, region, election_type,
                   candidate_id, candidate_name, vote_source, handled_by_node, verification_hash, voted_at
            FROM vote_tickets
            WHERE voter_id = ?
            ORDER BY voted_at DESC
            """,
            (rs, rowNum) -> new VoteTicketRecord(
                rs.getString("ticket_code"),
                rs.getString("voter_id"),
                rs.getString("voter_name"),
                rs.getString("city"),
                Region.fromString(rs.getString("region")),
                ElectionType.fromString(rs.getString("election_type")),
                rs.getString("candidate_id"),
                rs.getString("candidate_name"),
                VoteSource.fromString(rs.getString("vote_source")),
                rs.getString("handled_by_node"),
                rs.getString("verification_hash"),
                rs.getTimestamp("voted_at").toInstant()
            ),
            voterId.value()
        );
    }

    @Override
    @Transactional
    public boolean saveIfAbsent(VoteTicketRecord ticket) {
        try {
            jdbcTemplate.update(
                """
                INSERT INTO vote_tickets
                    (ticket_code, voter_id, voter_name, city, region, election_type,
                     candidate_id, candidate_name, vote_source, handled_by_node, verification_hash, voted_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                ticket.ticketCode(),
                ticket.voterId(),
                ticket.voterName(),
                ticket.city(),
                ticket.region().name(),
                ticket.electionType().name(),
                ticket.candidateId(),
                ticket.candidateName(),
                ticket.voteSource().name(),
                ticket.handledByNode(),
                ticket.verificationHash(),
                Timestamp.from(ticket.votedAt())
            );
            // Also upsert voter into registered_voters census
            jdbcTemplate.update(
                """
                INSERT INTO registered_voters (voter_id, full_name, city, region, eligible)
                VALUES (?, ?, ?, ?, TRUE)
                ON CONFLICT (voter_id) DO NOTHING
                """,
                ticket.voterId(), ticket.voterName(), ticket.city(), ticket.region().name()
            );
            return true;
        } catch (DuplicateKeyException ex) {
            return false;
        }
    }

    @Override
    public List<VoteTicketRecord> findRecent(int limit) {
        return jdbcTemplate.query(
            """
            SELECT ticket_code, voter_id, voter_name, city, region, election_type,
                   candidate_id, candidate_name, vote_source, handled_by_node, verification_hash, voted_at
            FROM vote_tickets
            ORDER BY voted_at DESC
            LIMIT ?
            """,
            (rs, rowNum) -> new VoteTicketRecord(
                rs.getString("ticket_code"),
                rs.getString("voter_id"),
                rs.getString("voter_name"),
                rs.getString("city"),
                Region.fromString(rs.getString("region")),
                ElectionType.fromString(rs.getString("election_type")),
                rs.getString("candidate_id"),
                rs.getString("candidate_name"),
                VoteSource.fromString(rs.getString("vote_source")),
                rs.getString("handled_by_node"),
                rs.getString("verification_hash"),
                rs.getTimestamp("voted_at").toInstant()
            ),
            Math.max(1, Math.min(limit, 500))
        );
    }

    @Override
    public String getLifecycleStateName() {
        try {
            String state = jdbcTemplate.queryForObject(
                "SELECT lifecycle_state FROM election_control WHERE id = 1", String.class
            );
            return state != null ? state : "HABILITADO";
        } catch (Exception e) {
            return "HABILITADO";
        }
    }

    @Override
    @Transactional
    public void updateLifecycleStateName(String stateName) {
        jdbcTemplate.update(
            "UPDATE election_control SET lifecycle_state = ?, updated_at = CURRENT_TIMESTAMP WHERE id = 1",
            stateName.toUpperCase()
        );
    }

    @Override
    public boolean isStrictCensusMode() {
        try {
            Boolean strict = jdbcTemplate.queryForObject(
                "SELECT strict_census_mode FROM election_control WHERE id = 1", Boolean.class
            );
            return Boolean.TRUE.equals(strict);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    @Transactional
    public void setStrictCensusMode(boolean strict) {
        jdbcTemplate.update(
            "UPDATE election_control SET strict_census_mode = ?, updated_at = CURRENT_TIMESTAMP WHERE id = 1",
            strict
        );
    }

    public Map<String, Object> readControlPanel() {
        try {
            return jdbcTemplate.queryForMap(
                """
                SELECT lifecycle_state, strict_census_mode, simulator_enabled,
                       simulator_rate_ms, simulator_batch_size, updated_at
                FROM election_control WHERE id = 1
                """
            );
        } catch (Exception e) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("lifecycle_state", "HABILITADO");
            fallback.put("strict_census_mode", false);
            fallback.put("simulator_enabled", true);
            fallback.put("simulator_rate_ms", 1800);
            fallback.put("simulator_batch_size", 2);
            fallback.put("updated_at", Instant.now().toString());
            return fallback;
        }
    }

    @Transactional
    public void updateSimulatorSettings(Boolean enabled, Integer rateMs, Integer batchSize, Boolean triggerBotAttack) {
        if (enabled != null) {
            jdbcTemplate.update("UPDATE election_control SET simulator_enabled = ?, updated_at = CURRENT_TIMESTAMP WHERE id = 1", enabled);
        }
        if (rateMs != null) {
            int clamped = Math.max(400, Math.min(rateMs, 10000));
            jdbcTemplate.update("UPDATE election_control SET simulator_rate_ms = ?, updated_at = CURRENT_TIMESTAMP WHERE id = 1", clamped);
        }
        if (batchSize != null) {
            int clamped = Math.max(1, Math.min(batchSize, 10));
            jdbcTemplate.update("UPDATE election_control SET simulator_batch_size = ?, updated_at = CURRENT_TIMESTAMP WHERE id = 1", clamped);
        }
        if (Boolean.TRUE.equals(triggerBotAttack)) {
            jdbcTemplate.update("UPDATE election_control SET bot_attack_requested = TRUE, updated_at = CURRENT_TIMESTAMP WHERE id = 1");
        }
    }

    public List<Map<String, Object>> queryAuditVotes(String electionType, String voteSource, String region, String search, int limit) {
        StringBuilder sql = new StringBuilder(
            """
            SELECT id, ticket_code, voter_id, candidate_id, candidate_name, city, region,
                   election_type, vote_source, handled_by_node, ip_address, voted_at
            FROM audit_votes WHERE 1=1
            """
        );
        List<Object> params = new ArrayList<>();
        if (electionType != null && !electionType.isBlank() && !"ALL".equalsIgnoreCase(electionType)) {
            sql.append(" AND election_type = ?");
            params.add(electionType.trim().toUpperCase());
        }
        if (voteSource != null && !voteSource.isBlank() && !"ALL".equalsIgnoreCase(voteSource)) {
            sql.append(" AND vote_source = ?");
            params.add(voteSource.trim().toUpperCase());
        }
        if (region != null && !region.isBlank() && !"ALL".equalsIgnoreCase(region)) {
            sql.append(" AND region = ?");
            params.add(region.trim().toUpperCase());
        }
        if (search != null && !search.isBlank()) {
            sql.append(" AND (LOWER(voter_id) LIKE ? OR LOWER(city) LIKE ? OR LOWER(ticket_code) LIKE ? OR LOWER(candidate_name) LIKE ?)");
            String like = "%" + search.trim().toLowerCase() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }
        sql.append(" ORDER BY id DESC LIMIT ?");
        params.add(Math.max(1, Math.min(limit, 300)));
        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    public List<Map<String, Object>> queryRegisteredVoters() {
        return jdbcTemplate.queryForList(
            """
            SELECT v.voter_id, v.full_name, v.city, v.region, v.eligible, v.registered_at,
                   EXISTS(SELECT 1 FROM vote_tickets t WHERE t.voter_id = v.voter_id AND t.election_type = 'NACIONAL') AS voted_nacional,
                   EXISTS(SELECT 1 FROM vote_tickets t WHERE t.voter_id = v.voter_id AND t.election_type = 'REGIONAL') AS voted_regional
            FROM registered_voters v
            ORDER BY v.registered_at DESC
            LIMIT 200
            """
        );
    }

    @Transactional
    public void upsertRegisteredVoter(String voterId, String fullName, String city, String region, boolean eligible) {
        jdbcTemplate.update(
            """
            INSERT INTO registered_voters (voter_id, full_name, city, region, eligible, registered_at)
            VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (voter_id) DO UPDATE
            SET full_name = EXCLUDED.full_name,
                city = EXCLUDED.city,
                region = EXCLUDED.region,
                eligible = EXCLUDED.eligible
            """,
            voterId, fullName, city, region, eligible
        );
    }

    @Transactional
    public void setVoterEligibility(String voterId, boolean eligible) {
        jdbcTemplate.update("UPDATE registered_voters SET eligible = ? WHERE voter_id = ?", eligible, voterId);
    }

    public List<Map<String, Object>> querySecurityIncidents(int limit) {
        return jdbcTemplate.queryForList(
            """
            SELECT id, ip_address, risk_level, reason, vote_count, detected_at
            FROM security_incidents
            ORDER BY detected_at DESC
            LIMIT ?
            """,
            Math.max(1, Math.min(limit, 200))
        );
    }

    public List<Map<String, Object>> queryNodeTelemetry() {
        return jdbcTemplate.queryForList(
            """
            SELECT node_id, availability_zone, requests_handled, votes_processed, last_heartbeat
            FROM node_telemetry
            ORDER BY node_id ASC
            """
        );
    }

    public Map<String, Object> queryDatabaseCounters() {
        try {
            return jdbcTemplate.queryForMap(
                """
                SELECT
                    (SELECT COUNT(*) FROM audit_votes) AS total_archived,
                    (SELECT COUNT(*) FROM audit_votes WHERE vote_source = 'REAL') AS real_archived,
                    (SELECT COUNT(*) FROM audit_votes WHERE vote_source = 'SIMULATED') AS simulated_archived,
                    (SELECT COUNT(*) FROM vote_tickets) AS total_tickets,
                    (SELECT COUNT(*) FROM registered_voters WHERE eligible = TRUE) AS eligible_voters,
                    (SELECT COUNT(*) FROM security_incidents) AS total_incidents
                """
            );
        } catch (Exception e) {
            log.debug("Failed querying DB counters: {}", e.getMessage());
            return Map.of();
        }
    }
}
