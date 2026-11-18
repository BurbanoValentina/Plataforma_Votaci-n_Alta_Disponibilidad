package com.voting.platform.domain.port;

/**
 * Outbound Port (DIP) for reading and updating shared electoral lifecycle state
 * (HABILITADO, EN_AUDITORIA, CERRADO) and census policy across distributed AWS nodes.
 */
public interface ElectionStateRepositoryPort {

    String getLifecycleStateName();

    void updateLifecycleStateName(String stateName);

    boolean isStrictCensusMode();

    void setStrictCensusMode(boolean strict);
}
