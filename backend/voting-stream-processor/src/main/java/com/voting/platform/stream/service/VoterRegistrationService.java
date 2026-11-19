package com.voting.platform.stream.service;

import com.voting.platform.domain.model.EligibleVoterRecord;
import com.voting.platform.domain.messaging.EligibleVoterPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * Startup & Census Synchronization Service.
 * Publishes eligible voter records into Kafka compacted topic eligible_voters.
 */
@Service
public class VoterRegistrationService {

    private final EligibleVoterPublisher publisher;

    public VoterRegistrationService(EligibleVoterPublisher publisher) {
        this.publisher = Objects.requireNonNull(publisher, "publisher cannot be null");
    }

    public int registerBatch(List<EligibleVoterRecord> voters) {
        Objects.requireNonNull(voters, "voters cannot be null");
        int count = 0;
        for (EligibleVoterRecord record : voters) {
            if (record != null) {
                publisher.publishEligibleVoter(record);
                count++;
            }
        }
        return count;
    }
}
