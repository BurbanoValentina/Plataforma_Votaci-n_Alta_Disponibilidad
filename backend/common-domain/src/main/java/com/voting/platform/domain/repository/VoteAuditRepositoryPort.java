package com.voting.platform.domain.repository;

import com.voting.platform.domain.model.VoteRecord;

/**
 * Port interface for PostgreSQL persistence of vote audits.
 */
public interface VoteAuditRepositoryPort {
    void saveAudit(VoteRecord vote);
    long count();
}

