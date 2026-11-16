package com.voting.platform.domain.port;

import com.voting.platform.domain.model.SecurityAlertRecord;

/**
 * Port interface for PostgreSQL persistence of security/fraud incidents.
 */
public interface SecurityIncidentRepositoryPort {
    void saveIncident(SecurityAlertRecord alert);
    long count();
}
