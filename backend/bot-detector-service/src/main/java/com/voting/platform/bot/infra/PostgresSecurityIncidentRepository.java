package com.voting.platform.bot.infra;

import com.voting.platform.domain.model.SecurityAlertRecord;
import com.voting.platform.domain.port.SecurityIncidentRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Repository Adapter implementing SecurityIncidentRepositoryPort for PostgreSQL.
 */
@Repository
public class PostgresSecurityIncidentRepository implements SecurityIncidentRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(PostgresSecurityIncidentRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final AtomicLong inMemoryCounter = new AtomicLong(0);

    public PostgresSecurityIncidentRepository(@Autowired(required = false) JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void saveIncident(SecurityAlertRecord alert) {
        Objects.requireNonNull(alert, "alert cannot be null");
        inMemoryCounter.incrementAndGet();

        if (jdbcTemplate != null) {
            try {
                jdbcTemplate.update(
                    "INSERT INTO security_incidents (id, ip_address, risk_level, reason, vote_count, detected_at) VALUES (?, ?, ?, ?, ?, ?)",
                    alert.alertId(),
                    alert.ipAddress().value(),
                    alert.riskLevel(),
                    alert.reason(),
                    alert.voteCountInWindow(),
                    Timestamp.from(alert.timestamp())
                );
                log.info("[POSTGRES AUDIT] Recorded security incident: id={}, ip={}, level={}, reason={}",
                    alert.alertId(), alert.ipAddress(), alert.riskLevel(), alert.reason());
            } catch (Exception e) {
                log.error("Failed to insert security incident into PostgreSQL: {}", e.getMessage());
            }
        } else {
            log.info("[POSTGRES AUDIT (IN-MEMORY)] Recorded security incident: id={}, ip={}, level={}",
                alert.alertId(), alert.ipAddress(), alert.riskLevel());
        }
    }

    @Override
    public long count() {
        if (jdbcTemplate != null) {
            try {
                Long cnt = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM security_incidents", Long.class);
                return cnt != null ? cnt : 0L;
            } catch (Exception e) {
                log.error("Error querying count from security_incidents: {}", e.getMessage());
            }
        }
        return inMemoryCounter.get();
    }
}
