package com.voting.platform.stream.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voting.platform.domain.model.EligibleVoterRecord;
import com.voting.platform.domain.model.SecurityAlertRecord;
import com.voting.platform.domain.model.VoteRecord;
import com.voting.platform.domain.port.EligibleVoterPublisher;
import com.voting.platform.domain.port.SecurityAlertPublisher;
import com.voting.platform.domain.port.VoteEventPublisher;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Properties;

/**
 * Unified Kafka Producer adapter for Stream Processor:
 * - eligible_voters (compacted topic, key = voterId)
 * - raw_votes (compacted topic, key = voterId:electionType, used by live simulator)
 * - security_alerts (key = ipAddress)
 */
@Component
public class KafkaStreamProducers implements EligibleVoterPublisher, SecurityAlertPublisher, VoteEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaStreamProducers.class);

    private final String bootstrapServers;
    private final String eligibleVotersTopic;
    private final String rawVotesTopic;
    private final String securityAlertsTopic;
    private final ObjectMapper objectMapper;
    private volatile KafkaProducer<String, String> producer;

    public KafkaStreamProducers(
        @Value("${voting.kafka.bootstrap-servers:kafka:9092}") String bootstrapServers,
        @Value("${voting.kafka.topic.eligible-voters:eligible_voters}") String eligibleVotersTopic,
        @Value("${voting.kafka.topic.raw-votes:raw_votes}") String rawVotesTopic,
        @Value("${voting.kafka.topic.security-alerts:security_alerts}") String securityAlertsTopic
    ) {
        this.bootstrapServers = bootstrapServers;
        this.eligibleVotersTopic = eligibleVotersTopic;
        this.rawVotesTopic = rawVotesTopic;
        this.securityAlertsTopic = securityAlertsTopic;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
        ensureProducer();
    }

    private synchronized KafkaProducer<String, String> ensureProducer() {
        if (producer != null) return producer;
        try {
            Properties props = new Properties();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            props.put(ProducerConfig.ACKS_CONFIG, "all");
            props.put(ProducerConfig.RETRIES_CONFIG, 3);
            props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 4000);
            this.producer = new KafkaProducer<>(props);
            log.info("KafkaStreamProducers connected to {}", bootstrapServers);
        } catch (Exception e) {
            log.warn("Kafka producer init deferred ({}): {}", bootstrapServers, e.getMessage());
        }
        return producer;
    }

    @Override
    public void publishEligibleVoter(EligibleVoterRecord eligibleVoter) {
        Objects.requireNonNull(eligibleVoter, "eligibleVoter cannot be null");
        sendJson(eligibleVotersTopic, eligibleVoter.voterId().value(), eligibleVoter);
    }

    @Override
    public void publishRawVote(VoteRecord vote) {
        Objects.requireNonNull(vote, "vote cannot be null");
        sendJson(rawVotesTopic, vote.kafkaKey(), vote);
    }

    @Override
    public void publishSecurityAlert(SecurityAlertRecord alert) {
        Objects.requireNonNull(alert, "alert cannot be null");
        log.warn("[KAFKA {}] SECURITY ALERT IP={} Level={} Votes={}",
            securityAlertsTopic, alert.ipAddress(), alert.riskLevel(), alert.voteCountInWindow());
        sendJson(securityAlertsTopic, alert.ipAddress().value(), alert);
    }

    private void sendJson(String topic, String key, Object payload) {
        KafkaProducer<String, String> p = ensureProducer();
        if (p == null) return;
        try {
            String json = objectMapper.writeValueAsString(payload);
            p.send(new ProducerRecord<>(topic, key, json), (meta, ex) -> {
                if (ex != null) {
                    log.error("Kafka publish failed on topic {}: {}", topic, ex.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("Kafka serialization error on topic {}: {}", topic, e.getMessage());
        }
    }

    @PreDestroy
    public void close() {
        if (producer != null) {
            try {
                producer.close();
            } catch (Exception ignored) {
            }
        }
    }
}
