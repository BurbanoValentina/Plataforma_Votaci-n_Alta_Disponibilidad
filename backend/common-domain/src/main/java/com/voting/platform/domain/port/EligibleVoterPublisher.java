package com.voting.platform.domain.port;

import com.voting.platform.domain.model.EligibleVoterRecord;

/**
 * Port interface for publishing eligible voter records to Kafka compacted topic eligible_voters.
 */
public interface EligibleVoterPublisher {
    void publishEligibleVoter(EligibleVoterRecord eligibleVoter);
}
