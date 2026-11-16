package com.voting.platform.bot.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voting.platform.bot.service.BotDetectorService;
import com.voting.platform.domain.model.CandidateId;
import com.voting.platform.domain.model.IpAddress;
import com.voting.platform.domain.model.Region;
import com.voting.platform.domain.model.VoteRecord;
import com.voting.platform.domain.model.VoterId;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Stateful Kafka Consumer for bot detection.
 * Consumes raw_votes, feeds each vote to BotDetectorService for sliding-window rate tracking
 * and GoF Risk State transitions (NORMAL, SOSPECHOSA, BLOQUEADA).
 */
@Component
public class KafkaVoteBotConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaVoteBotConsumer.class);

    private final BotDetectorService botService;
    private final String bootstrapServers;
    private final String topicName;
    private final String groupId;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread consumerThread;

    public KafkaVoteBotConsumer(
            BotDetectorService botService,
            @Value("${voting.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers,
            @Value("${voting.kafka.topic.raw-votes:raw_votes}") String topicName,
            @Value("${voting.kafka.group-id:bot-detector-group}") String groupId) {
        this.botService = botService;
        this.bootstrapServers = bootstrapServers;
        this.topicName = topicName;
        this.groupId = groupId;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    @PostConstruct
    public void start() {
        running.set(true);
        consumerThread = new Thread(this::consumeLoop, "kafka-bot-detector-consumer");
        consumerThread.setDaemon(false);
        consumerThread.start();
        log.info("Started Kafka Bot Detector Consumer thread for topic: {}", topicName);
    }

    private void consumeLoop() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");

        KafkaConsumer<String, String> consumer = null;
        while (running.get() && consumer == null) {
            try {
                consumer = new KafkaConsumer<>(props);
                consumer.subscribe(Collections.singletonList(topicName));
                log.info("Bot Detector subscribed to topic {} on broker {}", topicName, bootstrapServers);
            } catch (Exception e) {
                log.warn("Cannot connect to Kafka broker ({}). Retrying in 3 seconds: {}", bootstrapServers, e.getMessage());
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        try {
            while (running.get()) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> rec : records) {
                    try {
                        JsonNode node = objectMapper.readTree(rec.value());
                        String voterIdStr = node.path("voterId").path("value").asText(rec.key());
                        String candIdStr = node.path("candidateId").path("value").asText();
                        String regionStr = node.path("region").asText("NORTE");
                        String ipStr = node.path("ipAddress").path("value").asText("127.0.0.1");

                        VoteRecord vote = new VoteRecord(
                                VoterId.of(voterIdStr),
                                CandidateId.of(candIdStr),
                                Region.valueOf(regionStr),
                                IpAddress.of(ipStr),
                                Instant.now()
                        );
                        botService.inspectVote(vote);
                    } catch (Exception e) {
                        log.error("Failed to parse vote in bot detector: {}", rec.value(), e);
                    }
                }
            }
        } catch (Exception e) {
            if (running.get()) {
                log.error("Error in Kafka bot consumer loop", e);
            }
        } finally {
            if (consumer != null) {
                try {
                    consumer.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (consumerThread != null) {
            consumerThread.interrupt();
        }
    }
}
