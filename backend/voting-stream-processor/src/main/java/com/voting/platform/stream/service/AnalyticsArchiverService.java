package com.voting.platform.stream.service;

import com.voting.platform.domain.model.VoteRecord;
import com.voting.platform.domain.repository.VoteAuditRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Transactional Audit Archiver Service.
 * Decoupled from persistence details via VoteAuditRepositoryPort (DIP).
 */
@Service
public class AnalyticsArchiverService {

    private final VoteAuditRepositoryPort auditRepository;

    public AnalyticsArchiverService(VoteAuditRepositoryPort auditRepository) {
        this.auditRepository = Objects.requireNonNull(auditRepository, "auditRepository cannot be null");
    }

    public void archiveVote(VoteRecord vote) {
        Objects.requireNonNull(vote, "vote cannot be null");
        auditRepository.saveAudit(vote);
    }
}
