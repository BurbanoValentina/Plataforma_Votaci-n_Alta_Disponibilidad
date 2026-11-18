package com.voting.platform.domain.port;

import com.voting.platform.domain.model.SecurityAlertRecord;

import java.util.Optional;

/**
 * Outbound Port (DIP) for real-time duplicate / impersonation / IP fraud protection
 * synchronized across all load-balanced edge nodes.
 */
public interface FraudGuardPort {

    Optional<String> getBlockReason(String clientIp, String voterId);

    /**
     * Records a duplicate vote attempt. Detects identity impersonation (same cedula, different name)
     * and repeated spam attempts. Automatically blocks entity when threshold is exceeded.
     *
     * @return SecurityAlertRecord if a SUSPICIOUS or BLOCKED alert was raised.
     */
    Optional<SecurityAlertRecord> recordDuplicateOrImpersonationAttempt(
        String clientIp,
        String voterId,
        String registeredName,
        String attemptedName
    );

    void unblockEntity(String entityKey);
}
