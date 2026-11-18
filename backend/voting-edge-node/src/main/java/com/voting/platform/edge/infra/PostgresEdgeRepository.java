package com.voting.platform.edge.infra;

import com.voting.platform.domain.model.ElectionType;
import com.voting.platform.domain.model.GlobalResultRecord;
import com.voting.platform.domain.model.IpAddress;
import com.voting.platform.domain.model.Region;
import com.voting.platform.domain.model.RegionalResultRecord;
import com.voting.platform.domain.model.SecurityAlertRecord;
import com.voting.platform.domain.model.VoteSource;
import com.voting.platform.domain.model.VoteTicketRecord;
import com.voting.platform.domain.model.VoterId;
import com.voting.platform.domain.port.ElectionStateRepositoryPort;
import com.voting.platform.domain.port.FraudGuardPort;
import com.voting.platform.domain.port.VoteTicketRepositoryPort;
import com.voting.platform.domain.strategy.ElectoralWeightingStrategy;
import com.voting.platform.domain.strategy.RegionalStrategyContext;
import jakarta.annotation.PostConstruct;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared PostgreSQL Repository for distributed Edge Nodes:
 * - Atomic Vote Ticket storage (prevents duplicate cedula votes across AWS ALB nodes)
 * - Shared Election State (GoF State Pattern: HABILITADO / EN_AUDITORIA / CERRADO)
 * - Authoritative Global & Regional count reconciliation (guarantees zero flip-flop across ALB nodes)
 * - Real-time Duplicate & Identity Impersonation Fraud Guard (persists incidents & blocked entities)
 * - Telegram Bot API configuration storage
 */
@Repository
public class PostgresEdgeRepository implements VoteTicketRepositoryPort, ElectionStateRepositoryPort, FraudGuardPort {

    private static final Logger log = LoggerFactory.getLogger(PostgresEdgeRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final String nodeId;
    private final String availabilityZone;
    private final RegionalStrategyContext strategyContext = new RegionalStrategyContext(new ElectoralWeightingStrategy());
    private final ConcurrentHashMap<String, AtomicInteger> duplicateAttemptTracker = new ConcurrentHashMap<>();

    public PostgresEdgeRepository(
        JdbcTemplate jdbcTemplate,
        @Value("${voting.node-id:voting-edge-1}") String nodeId,
        @Value("${voting.availability-zone:us-east-1a}") String availabilityZone
    ) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate cannot be null");
        this.nodeId = nodeId;
        this.availabilityZone = availabilityZone;
    }

    @PostConstruct
    public void ensureExtendedSchema() {
        try {
            jdbcTemplate.execute("ALTER TABLE election_control ADD COLUMN IF NOT EXISTS telegram_enabled BOOLEAN NOT NULL DEFAULT FALSE");
            jdbcTemplate.execute("ALTER TABLE election_control ADD COLUMN IF NOT EXISTS telegram_bot_token VARCHAR(256) NOT NULL DEFAULT ''");
            jdbcTemplate.execute("ALTER TABLE election_control ADD COLUMN IF NOT EXISTS telegram_chat_id VARCHAR(128) NOT NULL DEFAULT ''");
            jdbcTemplate.execute("ALTER TABLE election_control ADD COLUMN IF NOT EXISTS telegram_notify_votes BOOLEAN NOT NULL DEFAULT TRUE");
            jdbcTemplate.execute("ALTER TABLE election_control ADD COLUMN IF NOT EXISTS telegram_notify_fraud BOOLEAN NOT NULL DEFAULT TRUE");
            jdbcTemplate.execute("ALTER TABLE election_control ADD COLUMN IF NOT EXISTS telegram_last_update_id BIGINT NOT NULL DEFAULT 0");
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS blocked_entities (
                    entity_key VARCHAR(128) PRIMARY KEY,
                    entity_type VARCHAR(32) NOT NULL,
                    reason VARCHAR(512) NOT NULL,
                    attempt_count INT NOT NULL DEFAULT 1,
                    blocked_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS telegram_activity_log (
                    id BIGSERIAL PRIMARY KEY,
                    actor VARCHAR(128) NOT NULL,
                    action_type VARCHAR(64) NOT NULL,
                    message VARCHAR(512) NOT NULL,
                    handled_by_node VARCHAR(64) NOT NULL,
                    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
            log.info("[{}] Verified extended PostgreSQL schema (Telegram + Activity Log + Fraud blocked_entities)", nodeId);
        } catch (Exception e) {
            log.warn("[{}] Extended schema check deferred: {}", nodeId, e.getMessage());
        }
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

    /**
     * Authoritative aggregation from PostgreSQL audit_votes (and real vote_tickets) so BOTH
     * AWS ALB edge nodes ALWAYS show 100% consistent REAL vs SIMULATED totals with zero flip-flop.
     */
    public GlobalResultRecord computeAuthoritativeGlobalResults() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT election_type, candidate_id, vote_source, COUNT(DISTINCT voter_id) AS cnt
                FROM audit_votes
                GROUP BY election_type, candidate_id, vote_source
                """
            );

            // Also include any REAL tickets that may not yet have finished Kafka -> Archiver round-trip
            List<Map<String, Object>> ticketRows = jdbcTemplate.queryForList(
                """
                SELECT election_type, candidate_id, COUNT(DISTINCT voter_id) AS cnt
                FROM vote_tickets
                WHERE vote_source = 'REAL'
                GROUP BY election_type, candidate_id
                """
            );

            Map<String, Long> totalMap = new HashMap<>();
            Map<String, Long> realMap = new HashMap<>();
            Map<String, Long> simMap = new HashMap<>();

            Map<String, Long> nacTotal = new HashMap<>();
            Map<String, Long> nacReal = new HashMap<>();
            Map<String, Long> nacSim = new HashMap<>();

            Map<String, Long> regTotal = new HashMap<>();
            Map<String, Long> regReal = new HashMap<>();
            Map<String, Long> regSim = new HashMap<>();

            for (Map<String, Object> r : rows) {
                String election = String.valueOf(r.get("election_type"));
                String cand = String.valueOf(r.get("candidate_id"));
                String source = String.valueOf(r.get("vote_source"));
                long cnt = ((Number) r.get("cnt")).longValue();

                if ("REAL".equalsIgnoreCase(source)) {
                    if ("NACIONAL".equalsIgnoreCase(election)) {
                        nacReal.merge(cand, cnt, Long::max);
                    } else {
                        regReal.merge(cand, cnt, Long::max);
                    }
                } else {
                    if ("NACIONAL".equalsIgnoreCase(election)) {
                        nacSim.merge(cand, cnt, Long::sum);
                    } else {
                        regSim.merge(cand, cnt, Long::sum);
                    }
                }
            }

            // Guarantee REAL citizen tickets appear immediately in REAL counts!
            for (Map<String, Object> t : ticketRows) {
                String election = String.valueOf(t.get("election_type"));
                String cand = String.valueOf(t.get("candidate_id"));
                long cnt = ((Number) t.get("cnt")).longValue();
                if ("NACIONAL".equalsIgnoreCase(election)) {
                    nacReal.merge(cand, cnt, Long::max);
                } else {
                    regReal.merge(cand, cnt, Long::max);
                }
            }

            // Consolidate maps cleanly
            for (String cand : unionKeys(nacReal, nacSim)) {
                long r = nacReal.getOrDefault(cand, 0L);
                long s = nacSim.getOrDefault(cand, 0L);
                nacTotal.put(cand, r + s);
                realMap.merge(cand, r, Long::sum);
                simMap.merge(cand, s, Long::sum);
                totalMap.merge(cand, r + s, Long::sum);
            }
            for (String cand : unionKeys(regReal, regSim)) {
                long r = regReal.getOrDefault(cand, 0L);
                long s = regSim.getOrDefault(cand, 0L);
                regTotal.put(cand, r + s);
                realMap.merge(cand, r, Long::sum);
                simMap.merge(cand, s, Long::sum);
                totalMap.merge(cand, r + s, Long::sum);
            }

            long sumReal = realMap.values().stream().mapToLong(Long::longValue).sum();
            long sumSim = simMap.values().stream().mapToLong(Long::longValue).sum();
            long sumNac = nacTotal.values().stream().mapToLong(Long::longValue).sum();
            long sumReg = regTotal.values().stream().mapToLong(Long::longValue).sum();

            return new GlobalResultRecord(
                totalMap, realMap, simMap,
                nacTotal, nacReal, nacSim,
                regTotal, regReal, regSim,
                sumReal + sumSim, sumReal, sumSim, sumNac, sumReg,
                Instant.now()
            );
        } catch (Exception e) {
            log.debug("[{}] Authoritative global query failed: {}", nodeId, e.getMessage());
            return GlobalResultRecord.empty();
        }
    }

    public Map<String, RegionalResultRecord> computeAuthoritativeRegionalResults() {
        Map<String, RegionalResultRecord> out = new LinkedHashMap<>();
        try {
            // ONLY Regional election votes (Eleccion Regional 2026) so National candidates never mix into Regional!
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT region, candidate_id, vote_source, COUNT(DISTINCT voter_id) AS cnt
                FROM audit_votes
                WHERE election_type = 'REGIONAL'
                GROUP BY region, candidate_id, vote_source
                """
            );
            List<Map<String, Object>> realTickets = jdbcTemplate.queryForList(
                """
                SELECT region, candidate_id, COUNT(DISTINCT voter_id) AS cnt
                FROM vote_tickets
                WHERE election_type = 'REGIONAL' AND vote_source = 'REAL'
                GROUP BY region, candidate_id
                """
            );

            Map<Region, Map<String, Long>> realByReg = new HashMap<>();
            Map<Region, Map<String, Long>> simByReg = new HashMap<>();
            for (Region r : Region.values()) {
                realByReg.put(r, new HashMap<>());
                simByReg.put(r, new HashMap<>());
            }
            for (Map<String, Object> row : rows) {
                Region reg = Region.fromString(String.valueOf(row.get("region")));
                String cand = String.valueOf(row.get("candidate_id"));
                String src = String.valueOf(row.get("vote_source"));
                long cnt = ((Number) row.get("cnt")).longValue();
                if ("REAL".equalsIgnoreCase(src)) {
                    realByReg.get(reg).merge(cand, cnt, Long::max);
                } else {
                    simByReg.get(reg).merge(cand, cnt, Long::sum);
                }
            }
            for (Map<String, Object> row : realTickets) {
                Region reg = Region.fromString(String.valueOf(row.get("region")));
                String cand = String.valueOf(row.get("candidate_id"));
                long cnt = ((Number) row.get("cnt")).longValue();
                realByReg.get(reg).merge(cand, cnt, Long::max);
            }
            for (Region reg : Region.values()) {
                Map<String, Long> reals = realByReg.get(reg);
                Map<String, Long> sims = simByReg.get(reg);
                Map<String, Long> raw = new HashMap<>();
                for (String cand : unionKeys(reals, sims)) {
                    raw.put(cand, reals.getOrDefault(cand, 0L) + sims.getOrDefault(cand, 0L));
                }
                long tot = raw.values().stream().mapToLong(Long::longValue).sum();
                long totR = reals.values().stream().mapToLong(Long::longValue).sum();
                long totS = sims.values().stream().mapToLong(Long::longValue).sum();
                double mult = strategyContext.getRegionalWeight(reg);
                Map<String, Double> weighted = strategyContext.executeWeighting(reg, raw);
                out.put(reg.name(), new RegionalResultRecord(
                    reg, weighted, Collections.unmodifiableMap(raw),
                    Collections.unmodifiableMap(reals), Collections.unmodifiableMap(sims),
                    tot, totR, totS, mult, Instant.now()
                ));
            }
        } catch (Exception e) {
            log.debug("[{}] Authoritative regional query failed: {}", nodeId, e.getMessage());
        }
        return out;
    }

    /**
     * Per-city live map telemetry (REAL vs SIMULATED counts) for the interactive Colombia Map.
     */
    public List<Map<String, Object>> queryCityMapActivity(String electionFilter) {
        try {
            String filterClause = (electionFilter != null && !electionFilter.isBlank() && !"ALL".equalsIgnoreCase(electionFilter))
                ? " WHERE election_type = '" + electionFilter.trim().toUpperCase().replace("'", "") + "'"
                : "";
            return jdbcTemplate.queryForList(
                """
                SELECT city,
                       MAX(region) AS region,
                       COUNT(*) AS total_votes,
                       SUM(CASE WHEN vote_source = 'REAL' THEN 1 ELSE 0 END) AS real_votes,
                       SUM(CASE WHEN vote_source = 'SIMULATED' THEN 1 ELSE 0 END) AS simulated_votes,
                       MAX(CASE WHEN vote_source = 'REAL' THEN voted_at ELSE NULL END) AS last_real_at,
                       MAX(voted_at) AS last_voted_at
                FROM audit_votes
                """ + filterClause + """
                 GROUP BY city
                ORDER BY total_votes DESC
                """
            );
        } catch (Exception e) {
            return List.of();
        }
    }

    @Transactional
    public void recordTelegramActivity(String actor, String actionType, String message) {
        try {
            jdbcTemplate.update(
                """
                INSERT INTO telegram_activity_log (actor, action_type, message, handled_by_node, created_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                actor != null ? actor : "Telegram",
                actionType != null ? actionType : "COMMAND",
                message != null ? message : "",
                nodeId
            );
        } catch (Exception e) {
            log.debug("[{}] Failed logging Telegram activity: {}", nodeId, e.getMessage());
        }
    }

    public List<Map<String, Object>> queryTelegramActivity(int limit) {
        try {
            return jdbcTemplate.queryForList(
                """
                SELECT id, actor, action_type, message, handled_by_node, created_at
                FROM telegram_activity_log
                ORDER BY id DESC
                LIMIT ?
                """,
                Math.max(1, Math.min(limit, 50))
            );
        } catch (Exception e) {
            return List.of();
        }
    }

    public long getTelegramLastUpdateId() {
        try {
            Long val = jdbcTemplate.queryForObject("SELECT telegram_last_update_id FROM election_control WHERE id = 1", Long.class);
            return val != null ? val : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    @Transactional
    public void updateTelegramLastUpdateId(long updateId) {
        jdbcTemplate.update(
            "UPDATE election_control SET telegram_last_update_id = GREATEST(telegram_last_update_id, ?) WHERE id = 1",
            updateId
        );
    }

    public boolean tryClaimTelegramPollSlot() {
        try {
            Boolean locked = jdbcTemplate.queryForObject("SELECT pg_try_advisory_lock(884422)", Boolean.class);
            return Boolean.TRUE.equals(locked);
        } catch (Exception e) {
            return false;
        }
    }

    public void releaseTelegramPollSlot() {
        try {
            jdbcTemplate.queryForObject("SELECT pg_advisory_unlock(884422)", Boolean.class);
        } catch (Exception ignored) {
        }
    }

    private static java.util.Set<String> unionKeys(Map<String, Long> a, Map<String, Long> b) {
        java.util.Set<String> keys = new java.util.HashSet<>(a.keySet());
        keys.addAll(b.keySet());
        return keys;
    }

    // ==================== FraudGuardPort Implementation ====================

    @Override
    public Optional<String> getBlockReason(String clientIp, String voterId) {
        try {
            List<String> reasons = jdbcTemplate.query(
                "SELECT reason FROM blocked_entities WHERE entity_key IN (?, ?) LIMIT 1",
                (rs, rowNum) -> rs.getString("reason"),
                "IP:" + clientIp, "CEDULA:" + voterId
            );
            return reasons.stream().findFirst();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public Optional<SecurityAlertRecord> recordDuplicateOrImpersonationAttempt(
        String clientIp,
        String voterId,
        String registeredName,
        String attemptedName
    ) {
        String safeIp = (clientIp != null && !clientIp.isBlank()) ? clientIp : "127.0.0.1";
        String regNorm = normalizeName(registeredName);
        String attNorm = normalizeName(attemptedName);

        boolean nameProvided = !attNorm.isEmpty() && !attNorm.startsWith("ciudadano c.c.");
        boolean regHasRealName = !regNorm.isEmpty() && !regNorm.startsWith("ciudadano c.c.");
        boolean nameMismatch = nameProvided && regHasRealName && !regNorm.equals(attNorm);

        String counterKey = safeIp + "|" + voterId;
        int attempts = duplicateAttemptTracker.computeIfAbsent(counterKey, k -> new AtomicInteger(0)).incrementAndGet();

        // Also count recent incidents for this IP in DB so detection works cleanly across BOTH AWS nodes!
        int dbRecent = 0;
        try {
            Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM security_incidents WHERE ip_address = ? AND detected_at > NOW() - INTERVAL '15 minutes'",
                Integer.class, safeIp
            );
            if (c != null) dbRecent = c;
        } catch (Exception ignored) {
        }
        int totalAttempts = Math.max(attempts, dbRecent + 1);

        String riskLevel = null;
        String reason = null;
        boolean shouldBlock = false;

        if (nameMismatch) {
            if (totalAttempts >= 2) {
                riskLevel = "BLOQUEADA";
                shouldBlock = true;
                reason = String.format(
                    "SUPLANTACIÓN DE IDENTIDAD BLOQUEADA: Cédula %s intentó votar repetidamente con nombre diferente ('%s' vs registrado '%s'). IP %s bloqueada.",
                    voterId, attemptedName, registeredName, safeIp
                );
            } else {
                riskLevel = "SOSPECHOSA";
                reason = String.format(
                    "Posible suplantación de identidad: Cédula %s ya votó como '%s' pero intentó votar nuevamente como '%s' desde IP %s.",
                    voterId, registeredName, attemptedName, safeIp
                );
            }
        } else if (totalAttempts >= 3) {
            riskLevel = "BLOQUEADA";
            shouldBlock = true;
            reason = String.format(
                "ABUSO DE VOTO DUPLICADO BLOQUEADO: %d intentos repetidos con la cédula %s desde IP %s. Acceso bloqueado automáticamente.",
                totalAttempts, voterId, safeIp
            );
        } else if (totalAttempts == 2) {
            riskLevel = "SOSPECHOSA";
            reason = String.format(
                "Reincidencia de voto duplicado detectada: %d intentos repetidos con cédula %s desde IP %s.",
                totalAttempts, voterId, safeIp
            );
        }

        if (riskLevel == null) {
            return Optional.empty();
        }

        SecurityAlertRecord alert = SecurityAlertRecord.of(IpAddress.of(safeIp), riskLevel, reason, totalAttempts);
        try {
            jdbcTemplate.update(
                """
                INSERT INTO security_incidents (id, ip_address, risk_level, reason, vote_count, detected_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """,
                alert.alertId(), safeIp, riskLevel, reason, totalAttempts, Timestamp.from(alert.timestamp())
            );
            if (shouldBlock) {
                jdbcTemplate.update(
                    """
                    INSERT INTO blocked_entities (entity_key, entity_type, reason, attempt_count, blocked_at)
                    VALUES (?, 'IP', ?, ?, CURRENT_TIMESTAMP)
                    ON CONFLICT (entity_key) DO UPDATE
                    SET reason = EXCLUDED.reason,
                        attempt_count = blocked_entities.attempt_count + 1,
                        blocked_at = CURRENT_TIMESTAMP
                    """,
                    "IP:" + safeIp, reason, totalAttempts
                );
                if (nameMismatch) {
                    jdbcTemplate.update(
                        """
                        INSERT INTO blocked_entities (entity_key, entity_type, reason, attempt_count, blocked_at)
                        VALUES (?, 'CEDULA', ?, ?, CURRENT_TIMESTAMP)
                        ON CONFLICT (entity_key) DO UPDATE
                        SET reason = EXCLUDED.reason,
                            attempt_count = blocked_entities.attempt_count + 1,
                            blocked_at = CURRENT_TIMESTAMP
                        """,
                        "CEDULA:" + voterId, reason, totalAttempts
                    );
                }
            }
        } catch (Exception e) {
            log.warn("[{}] Failed recording fraud incident: {}", nodeId, e.getMessage());
        }
        return Optional.of(alert);
    }

    @Override
    @Transactional
    public void unblockEntity(String entityKey) {
        jdbcTemplate.update("DELETE FROM blocked_entities WHERE entity_key = ? OR entity_key = ? OR entity_key = ?",
            entityKey, "IP:" + entityKey, "CEDULA:" + entityKey);
        duplicateAttemptTracker.keySet().removeIf(k -> k.contains(entityKey));
    }

    public List<Map<String, Object>> queryBlockedEntities() {
        try {
            return jdbcTemplate.queryForList(
                "SELECT entity_key, entity_type, reason, attempt_count, blocked_at FROM blocked_entities ORDER BY blocked_at DESC"
            );
        } catch (Exception e) {
            return List.of();
        }
    }

    private String normalizeName(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    // ==================== Tickets & Voters ====================

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

    // ==================== Election State & Telegram Control ====================

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
            Map<String, Object> row = new LinkedHashMap<>(jdbcTemplate.queryForMap(
                """
                SELECT lifecycle_state, strict_census_mode, simulator_enabled,
                       simulator_rate_ms, simulator_batch_size,
                       telegram_enabled, telegram_chat_id, telegram_notify_votes, telegram_notify_fraud,
                       (CASE WHEN LENGTH(TRIM(COALESCE(telegram_bot_token, ''))) > 5 THEN TRUE ELSE FALSE END) AS telegram_token_configured,
                       updated_at
                FROM election_control WHERE id = 1
                """
            ));
            return row;
        } catch (Exception e) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("lifecycle_state", "HABILITADO");
            fallback.put("strict_census_mode", false);
            fallback.put("simulator_enabled", true);
            fallback.put("simulator_rate_ms", 1800);
            fallback.put("simulator_batch_size", 2);
            fallback.put("telegram_enabled", false);
            fallback.put("telegram_token_configured", false);
            fallback.put("telegram_chat_id", "");
            fallback.put("updated_at", Instant.now().toString());
            return fallback;
        }
    }

    public Map<String, Object> readTelegramRawConfig() {
        try {
            return jdbcTemplate.queryForMap(
                """
                SELECT telegram_enabled, telegram_bot_token, telegram_chat_id,
                       telegram_notify_votes, telegram_notify_fraud
                FROM election_control WHERE id = 1
                """
            );
        } catch (Exception e) {
            return Map.of();
        }
    }

    @Transactional
    public void updateTelegramConfig(Boolean enabled, String botToken, String chatId, Boolean notifyVotes, Boolean notifyFraud) {
        if (enabled != null) {
            jdbcTemplate.update("UPDATE election_control SET telegram_enabled = ?, updated_at = CURRENT_TIMESTAMP WHERE id = 1", enabled);
        }
        if (botToken != null && !botToken.isBlank() && !botToken.contains("****")) {
            jdbcTemplate.update("UPDATE election_control SET telegram_bot_token = ?, updated_at = CURRENT_TIMESTAMP WHERE id = 1", botToken.trim());
        }
        if (chatId != null) {
            jdbcTemplate.update("UPDATE election_control SET telegram_chat_id = ?, updated_at = CURRENT_TIMESTAMP WHERE id = 1", chatId.trim());
        }
        if (notifyVotes != null) {
            jdbcTemplate.update("UPDATE election_control SET telegram_notify_votes = ?, updated_at = CURRENT_TIMESTAMP WHERE id = 1", notifyVotes);
        }
        if (notifyFraud != null) {
            jdbcTemplate.update("UPDATE election_control SET telegram_notify_fraud = ?, updated_at = CURRENT_TIMESTAMP WHERE id = 1", notifyFraud);
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
                    (SELECT COUNT(*) FROM security_incidents) AS total_incidents,
                    (SELECT COUNT(*) FROM blocked_entities) AS total_blocked
                """
            );
        } catch (Exception e) {
            log.debug("Failed querying DB counters: {}", e.getMessage());
            return Map.of();
        }
    }
}
