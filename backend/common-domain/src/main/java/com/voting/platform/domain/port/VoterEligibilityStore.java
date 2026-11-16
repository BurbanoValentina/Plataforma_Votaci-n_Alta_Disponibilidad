package com.voting.platform.domain.port;

import com.voting.platform.domain.model.EligibleVoterRecord;
import com.voting.platform.domain.model.VoterId;

/**
 * Port interface for the in-memory KTable / local state tracking eligible voters.
 */
public interface VoterEligibilityStore {
    boolean isEligible(VoterId voterId);
    void registerEligible(EligibleVoterRecord record);
    void removeEligible(VoterId voterId);
    int size();
    void clear();
}
