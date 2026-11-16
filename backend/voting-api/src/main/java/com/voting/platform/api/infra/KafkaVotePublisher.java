package com.voting.platform.api.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voting.platform.domain.model.VoteRecord;
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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapter implementing VoteEventPublisher.
 * Publishes raw votes to Kafka compacted topic raw_votes using voterId as partition key.
 */
@Component
public class KafkaVotePublisher implements VoteEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaVotePublisher.class);

    private final String topicName;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, VoteRecord> publishedVotes = new ConcurrentHashMap<>();
    private KafkaProducer<String, String> producer;

    public KafkaVotePublisher(
            @Value("${voting.kafka.topic.raw-votes:raw_votes}") String topicName,
            @Value("${voting.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
        this.topicName = topicName;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();

        try {
            Properties props = new Properties();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            props.put(ProducerConfig.ACKS_CONFIG, "all");
            props.put(ProducerConfig.RETRIES_CONFIG, 3);
            props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 3000);
            this.producer = new KafkaProducer<>(props);
            log.info("Kafka Producer successfully initialized for broker: {}", bootstrapServers);
        } catch (Exception e) {
            log.warn("Kafka broker not reachable at startup ({}), falling back to in-memory: {}", bootstrapServers, e.getMessage());
            this.producer = null;
        }
    }

    @Override
    public void publishRawVote(VoteRecord vote) {
        Objects.requireNonNull(vote, "vote cannot be null");
        String key = vote.voterId().value();
        publishedVotes.put(key, vote);

        if (producer != null) {
            try {
                String json = objectMapper.writeValueAsString(vote);
                ProducerRecord<String, String> record = new ProducerRecord<>(topicName, key, json);
                producer.send(record, (metadata, exception) -> {
                    if (exception != null) {
                        log.error("Failed to send vote to Kafka: {}", exception.getMessage());
                    } else {
                        log.info("[KAFKA COMPACTED TOPIC: {}] Partition Key: {}, Partition: {}, Offset: {}",
                                topicName, key, metadata.partition(), metadata.offset());
                    }
                });
            } catch (Exception e) {
                log.error("Error serializing or sending vote to Kafka: {}", e.getMessage());
            }
        } else {
            log.info("[KAFKA COMPACTED TOPIC: {}] Partition Key (user_id): {}, Payload: {}", topicName, key, vote);
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
        return publishedVotes.size();
    }

    public VoteRecord getLatestVote(String voterId) {
        return publishedVotes.get(voterId);
    }
}
