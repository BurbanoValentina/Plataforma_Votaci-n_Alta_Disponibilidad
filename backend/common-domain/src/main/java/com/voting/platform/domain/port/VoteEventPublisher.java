package com.voting.platform.domain.port;

import com.voting.platform.domain.model.VoteRecord;

/**
 * Port interface for publishing raw votes to Kafka compacted topic raw_votes.
 */
public interface VoteEventPublisher {
    void publishRawVote(VoteRecord vote);
}
