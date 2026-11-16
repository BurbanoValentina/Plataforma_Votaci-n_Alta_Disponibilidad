package com.voting.platform.registration.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voting.platform.domain.model.EligibleVoterRecord;
import com.voting.platform.domain.port.EligibleVoterPublisher;
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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapter implementation of EligibleVoterPublisher.
 * Emits eligible voters to Kafka compacted topic eligible_voters.
 */
@Component
public class LoggingEligibleVoterPublisher implements EligibleVoterPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingEligibleVoterPublisher.class);

    private final String topicName;
    private final ConcurrentHashMap<String, EligibleVoterRecord> publishedVoters;
    private final ObjectMapper objectMapper;
    private KafkaProducer<String, String> producer;

    public LoggingEligibleVoterPublisher(
            @Value("${voting.kafka.topic.eligible-voters:eligible_voters}") String topicName,
            @Value("${voting.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
        this.topicName = topicName;
        this.publishedVoters = new ConcurrentHashMap<>();
        this.objectMapper = new ObjectMapper().findAndRegisterModules();

        try {
            Properties props = new Properties();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            props.put(ProducerConfig.ACKS_CONFIG, "all");
            props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 3000);
            this.producer = new KafkaProducer<>(props);
            log.info("Kafka Producer for Eligible Voters initialized for broker: {}", bootstrapServers);
        } catch (Exception e) {
            log.warn("Kafka broker unreachable for voter registration ({}), using fallback: {}", bootstrapServers, e.getMessage());
            this.producer = null;
        }
    }

    @Override
    public void publishEligibleVoter(EligibleVoterRecord eligibleVoter) {
        Objects.requireNonNull(eligibleVoter, "eligibleVoter cannot be null");
        String key = eligibleVoter.voterId().value();
        publishedVoters.put(key, eligibleVoter);
        log.info("[KAFKA COMPACTED TOPIC: {}] Key: {}, Payload: {}", topicName, key, eligibleVoter);

        if (producer != null) {
            try {
                String json = objectMapper.writeValueAsString(eligibleVoter);
                producer.send(new ProducerRecord<>(topicName, key, json));
            } catch (Exception e) {
                log.error("Failed to publish eligible voter to Kafka: {}", e.getMessage());
            }
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

    public int getPublishedCount() {
        return publishedVoters.size();
    }

    public EligibleVoterRecord getPublished(String voterId) {
        return publishedVoters.get(voterId);
    }
}
