package com.voting.platform.domain.messaging;

import com.voting.platform.domain.model.SecurityAlertRecord;
import com.voting.platform.domain.dto.response.VoteTicketRecord;

/**
 * Outbound Port (DIP) for real-time external alerts (e.g. Telegram Bot API).
 */
public interface NotificationPort {

    void notifyRealVote(VoteTicketRecord ticket);

    void notifyFraudAlert(SecurityAlertRecord alert);

    void notifyLifecycleChange(String newState, String nodeId);
}

