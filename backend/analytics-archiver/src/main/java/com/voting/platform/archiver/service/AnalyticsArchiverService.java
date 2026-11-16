package com.voting.platform.archiver.service;

import com.voting.platform.domain.model.VoteRecord;
import com.voting.platform.domain.port.VoteAuditRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Service responsible for creating an immutable audit trail of incoming votes in PostgreSQL.
 * Follows Dependency Inversion Principle with strict constructor injection.
 */
@Service
public class AnalyticsArchiverService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsArchiverService.class);

    private final VoteAuditRepositoryPort auditRepository;

    public AnalyticsArchiverService(VoteAuditRepositoryPort auditRepository) {
        this.auditRepository = Objects.requireNonNull(auditRepository, "auditRepository cannot be null");
    }

    /**
     * Translates a Kafka vote event into persistent relational audit entity.
     */
    public void archiveVote(VoteRecord vote) {
        Objects.requireNonNull(vote, "vote cannot be null");
        auditRepository.saveAudit(vote);
        log.debug("Archived vote for voter: {}", vote.voterId());
    }

    public long getArchivedVoteCount() {
        return auditRepository.count();
    }
}
