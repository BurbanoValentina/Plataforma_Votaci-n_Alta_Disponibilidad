package com.voting.platform.archiver.infra;

import com.voting.platform.domain.model.VoteRecord;
import com.voting.platform.domain.port.VoteAuditRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Repository Adapter implementing VoteAuditRepositoryPort for PostgreSQL.
 */
@Repository
public class PostgresVoteAuditRepository implements VoteAuditRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(PostgresVoteAuditRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final AtomicLong inMemoryCounter = new AtomicLong(0);

    public PostgresVoteAuditRepository(@Autowired(required = false) JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void saveAudit(VoteRecord vote) {
        Objects.requireNonNull(vote, "vote cannot be null");
        inMemoryCounter.incrementAndGet();

        if (jdbcTemplate != null) {
            try {
                jdbcTemplate.update(
                    "INSERT INTO audit_votes (voter_id, candidate_id, region, ip_address, voted_at) VALUES (?, ?, ?, ?, ?)",
                    vote.voterId().value(),
                    vote.candidateId().value(),
                    vote.region().name(),
                    vote.ipAddress().value(),
                    Timestamp.from(vote.timestamp())
                );
                log.info("[POSTGRES AUDIT] Persisted vote audit trail: voter={}, candidate={}, region={}",
                    vote.voterId(), vote.candidateId(), vote.region());
            } catch (Exception e) {
                log.error("Failed to insert vote into PostgreSQL audit_votes: {}", e.getMessage());
            }
        } else {
            log.info("[POSTGRES AUDIT (IN-MEMORY)] Persisted vote audit trail: voter={}, candidate={}",
                vote.voterId(), vote.candidateId());
        }
    }

    @Override
    public long count() {
        if (jdbcTemplate != null) {
            try {
                Long cnt = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_votes", Long.class);
                return cnt != null ? cnt : 0L;
            } catch (Exception e) {
                log.error("Error querying count from audit_votes: {}", e.getMessage());
            }
        }
        return inMemoryCounter.get();
    }
}
