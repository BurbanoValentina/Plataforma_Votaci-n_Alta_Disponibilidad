package com.voting.platform.edge.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voting.platform.domain.model.EligibleVoterRecord;
import com.voting.platform.domain.model.Region;
import com.voting.platform.domain.model.SecurityAlertRecord;
import com.voting.platform.domain.model.VoteRecord;
import com.voting.platform.domain.model.VoterId;
import com.voting.platform.domain.port.EligibleVoterPublisher;
import com.voting.platform.domain.port.SecurityAlertPublisher;
import com.voting.platform.domain.port.VoteEventPublisher;
import com.voting.platform.edge.infra.InMemoryEligibilityStore;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Edge Node Kafka Client:
 * 1. Consumes compacted topic `eligible_voters` from beginning (unique group per node)
 *    to materialize the local in-memory KTable in sync across all AWS App Nodes.
 * 2. Publishes verified votes to compacted topic `raw_votes` keyed by voterId:electionType.
 * 3. Publishes newly enrolled / revoked voters to compacted topic `eligible_voters`.
 * 4. Publishes fraud & impersonation alerts to Kafka topic `security_alerts`.
 */
@Component
public class KafkaEdgeClient implements VoteEventPublisher, EligibleVoterPublisher, SecurityAlertPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaEdgeClient.class);

    private final String bootstrapServers;
    private final String eligibleVotersTopic;
    private final String rawVotesTopic;
    private final String securityAlertsTopic;
    private final String nodeId;
    private final InMemoryEligibilityStore eligibilityStore;
    private final ObjectMapper objectMapper;

    private volatile KafkaProducer<String, String> producer;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final ExecutorService consumerExecutor = Executors.newSingleThreadExecutor();

    public KafkaEdgeClient(
        @Value("${voting.kafka.bootstrap-servers:kafka:9092}") String bootstrapServers,
        @Value("${voting.kafka.topic.eligible-voters:eligible_voters}") String eligibleVotersTopic,
        @Value("${voting.kafka.topic.raw-votes:raw_votes}") String rawVotesTopic,
        @Value("${voting.kafka.topic.security-alerts:security_alerts}") String securityAlertsTopic,
        @Value("${voting.node-id:voting-edge-1}") String nodeId,
        InMemoryEligibilityStore eligibilityStore
    ) {
        this.bootstrapServers = bootstrapServers;
        this.eligibleVotersTopic = eligibleVotersTopic;
        this.rawVotesTopic = rawVotesTopic;
        this.securityAlertsTopic = securityAlertsTopic;
        this.nodeId = nodeId;
        this.eligibilityStore = Objects.requireNonNull(eligibilityStore, "eligibilityStore cannot be null");
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
        ensureProducer();
    }

    @PostConstruct
    public void startKTableSync() {
        consumerExecutor.submit(this::consumeEligibleVotersLoop);
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
            log.info("[{}] KafkaEdgeClient producer connected to {}", nodeId, bootstrapServers);
        } catch (Exception e) {
            log.warn("[{}] Kafka producer deferred ({}): {}", nodeId, bootstrapServers, e.getMessage());
        }
        return producer;
    }

    @Override
    public void publishRawVote(VoteRecord vote) {
        Objects.requireNonNull(vote, "vote cannot be null");
        KafkaProducer<String, String> p = ensureProducer();
        if (p == null) return;
        try {
            String json = objectMapper.writeValueAsString(vote);
            p.send(new ProducerRecord<>(rawVotesTopic, vote.kafkaKey(), json), (meta, ex) -> {
                if (ex != null) {
                    log.error("[{}] Kafka raw_votes send error: {}", nodeId, ex.getMessage());
                }
            });
            p.flush();
        } catch (Exception e) {
            log.error("[{}] Failed serializing VoteRecord: {}", nodeId, e.getMessage());
        }
    }

    @Override
    public void publishEligibleVoter(EligibleVoterRecord eligibleVoter) {
        Objects.requireNonNull(eligibleVoter, "eligibleVoter cannot be null");
        KafkaProducer<String, String> p = ensureProducer();
        if (p == null) return;
        try {
            String json = objectMapper.writeValueAsString(eligibleVoter);
            p.send(new ProducerRecord<>(eligibleVotersTopic, eligibleVoter.voterId().value(), json));
        } catch (Exception e) {
            log.error("[{}] Failed publishing eligible voter: {}", nodeId, e.getMessage());
        }
    }

    @Override
    public void publishSecurityAlert(SecurityAlertRecord alert) {
        Objects.requireNonNull(alert, "alert cannot be null");
        KafkaProducer<String, String> p = ensureProducer();
        if (p == null) return;
        try {
            String json = objectMapper.writeValueAsString(alert);
            p.send(new ProducerRecord<>(securityAlertsTopic, alert.ipAddress().value(), json));
        } catch (Exception e) {
            log.error("[{}] Failed publishing security alert: {}", nodeId, e.getMessage());
        }
    }

    public void publishVoterRevocation(VoterId voterId) {
        Objects.requireNonNull(voterId, "voterId cannot be null");
        eligibilityStore.removeEligible(voterId);
        KafkaProducer<String, String> p = ensureProducer();
        if (p == null) return;
        p.send(new ProducerRecord<>(eligibleVotersTopic, voterId.value(), null));
    }

    private void consumeEligibleVotersLoop() {
        String uniqueGroupId = "edge-ktable-" + nodeId + "-" + UUID.randomUUID().toString().substring(0, 8);
        while (running.get()) {
            Properties props = new Properties();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            props.put(ConsumerConfig.GROUP_ID_CONFIG, uniqueGroupId);
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");

            try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
                consumer.subscribe(Collections.singletonList(eligibleVotersTopic));
                log.info("[{}] KTable consumer subscribed to '{}' fromBeginning", nodeId, eligibleVotersTopic);

                while (running.get()) {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                    for (ConsumerRecord<String, String> rec : records) {
                        try {
                            if (rec.value() == null || rec.value().isBlank()) {
                                if (rec.key() != null && !rec.key().isBlank()) {
                                    eligibilityStore.removeEligible(VoterId.of(rec.key()));
                                }
                            } else {
                                JsonNode n = objectMapper.readTree(rec.value());
                                String idStr = n.has("voterId") && n.get("voterId").isObject()
                                    ? n.path("voterId").path("value").asText()
                                    : n.path("voterId").asText(rec.key());
                                String name = n.path("fullName").asText("Ciudadano");
                                String regStr = n.path("region").asText("CENTRO");
                                if (idStr != null && !idStr.isBlank()) {
                                    eligibilityStore.registerEligible(new EligibleVoterRecord(
                                        VoterId.of(idStr),
                                        name,
                                        Region.fromString(regStr),
                                        Instant.now()
                                    ));
                                }
                            }
                        } catch (Exception ex) {
                            log.debug("[{}] Ignored malformed KTable entry: {}", nodeId, ex.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("[{}] Kafka KTable consumer reconnecting in 3s: {}", nodeId, e.getMessage());
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    @PreDestroy
    public void close() {
        running.set(false);
        consumerExecutor.shutdownNow();
        if (producer != null) {
            try {
                producer.close();
            } catch (Exception ignored) {
            }
        }
    }
}
