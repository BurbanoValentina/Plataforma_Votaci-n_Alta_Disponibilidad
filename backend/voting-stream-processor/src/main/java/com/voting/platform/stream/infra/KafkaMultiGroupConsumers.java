package com.voting.platform.stream.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voting.platform.domain.model.EligibleVoterRecord;
import com.voting.platform.domain.model.Region;
import com.voting.platform.domain.model.VoteRecord;
import com.voting.platform.domain.model.VoterId;
import com.voting.platform.stream.service.AnalyticsArchiverService;
import com.voting.platform.stream.service.BotDetectorService;
import com.voting.platform.stream.service.RegionalRollupService;
import com.voting.platform.stream.service.VoteProcessorService;
import com.voting.platform.stream.service.VoterRegistrationService;
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

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Orchestrates 4 independent stateful Kafka Consumer Groups reading raw_votes concurrently:
 * 1. global-vote-processor -> VoteCounterAggregate (Fanout 1s)
 * 2. regional-rollup-service -> RegionalVoteCounterAggregate + Strategy Pattern (Topic 5s)
 * 3. bot-detector-service -> BotDetectorAggregate + State Pattern (security_alerts + PostgreSQL)
 * 4. analytics-archiver-service -> Transactional PostgreSQL audit_votes persistence
 * Also bootstraps eligible_voters compacted topic at startup.
 */
@Component
public class KafkaMultiGroupConsumers {

    private static final Logger log = LoggerFactory.getLogger(KafkaMultiGroupConsumers.class);

    private final String bootstrapServers;
    private final String rawVotesTopic;
    private final VoteProcessorService voteProcessorService;
    private final RegionalRollupService regionalRollupService;
    private final BotDetectorService botDetectorService;
    private final AnalyticsArchiverService analyticsArchiverService;
    private final VoterRegistrationService voterRegistrationService;
    private final PostgresStreamRepository postgresRepository;
    private final ObjectMapper objectMapper;

    private final AtomicBoolean running = new AtomicBoolean(true);
    private final ExecutorService executor = Executors.newFixedThreadPool(5);

    public KafkaMultiGroupConsumers(
        @Value("${voting.kafka.bootstrap-servers:kafka:9092}") String bootstrapServers,
        @Value("${voting.kafka.topic.raw-votes:raw_votes}") String rawVotesTopic,
        VoteProcessorService voteProcessorService,
        RegionalRollupService regionalRollupService,
        BotDetectorService botDetectorService,
        AnalyticsArchiverService analyticsArchiverService,
        VoterRegistrationService voterRegistrationService,
        PostgresStreamRepository postgresRepository
    ) {
        this.bootstrapServers = bootstrapServers;
        this.rawVotesTopic = rawVotesTopic;
        this.voteProcessorService = voteProcessorService;
        this.regionalRollupService = regionalRollupService;
        this.botDetectorService = botDetectorService;
        this.analyticsArchiverService = analyticsArchiverService;
        this.voterRegistrationService = voterRegistrationService;
        this.postgresRepository = postgresRepository;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    @PostConstruct
    public void start() {
        // Hydrate stateful aggregates from PostgreSQL audit_votes so restarts preserve tallies
        List<VoteRecord> historical = postgresRepository.loadHistoricalVotes();
        for (VoteRecord vote : historical) {
            voteProcessorService.processVote(vote);
            regionalRollupService.processRegionalVote(vote);
        }
        log.info("Hydrated {} historical votes from PostgreSQL into stateful aggregates", historical.size());

        // Publish initial eligible census into Kafka compacted topic eligible_voters
        executor.submit(this::bootstrapEligibleVoters);

        // Launch 4 independent Kafka Consumer Groups over raw_votes
        executor.submit(() -> consumeLoop("global-vote-processor", "earliest", voteProcessorService::processVote));
        executor.submit(() -> consumeLoop("regional-rollup-service", "earliest", regionalRollupService::processRegionalVote));
        executor.submit(() -> consumeLoop("bot-detector-service", "latest", botDetectorService::analyzeVote));
        executor.submit(() -> consumeLoop("analytics-archiver-service", "latest", analyticsArchiverService::archiveVote));
    }

    private void bootstrapEligibleVoters() {
        try {
            Thread.sleep(2500);
            List<EligibleVoterRecord> voters = new ArrayList<>(postgresRepository.findAllEligibleVoters());
            if (voters.isEmpty()) {
                try (InputStream is = getClass().getResourceAsStream("/voters.json")) {
                    if (is != null) {
                        JsonNode array = objectMapper.readTree(is);
                        if (array.isArray()) {
                            for (JsonNode n : array) {
                                voters.add(new EligibleVoterRecord(
                                    VoterId.of(n.path("voterId").asText()),
                                    n.path("fullName").asText("Ciudadano"),
                                    Region.fromString(n.path("region").asText("CENTRO")),
                                    Instant.now()
                                ));
                            }
                        }
                    }
                }
            }
            int published = voterRegistrationService.registerBatch(voters);
            log.info("Published {} eligible voters to Kafka compacted topic eligible_voters", published);
        } catch (Exception e) {
            log.warn("Eligible voters bootstrap encountered error: {}", e.getMessage());
        }
    }

    private void consumeLoop(String groupId, String offsetReset, Consumer<VoteRecord> handler) {
        while (running.get()) {
            Properties props = new Properties();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, offsetReset);
            props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");

            try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
                consumer.subscribe(Collections.singletonList(rawVotesTopic));
                log.info("Kafka consumer group '{}' subscribed to '{}'", groupId, rawVotesTopic);

                while (running.get()) {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(400));
                    for (ConsumerRecord<String, String> rec : records) {
                        try {
                            VoteRecord vote = parseVote(rec.value());
                            if (vote != null) {
                                handler.accept(vote);
                            }
                        } catch (Exception ex) {
                            log.warn("[{}] Skipping malformed vote record: {}", groupId, ex.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("[{}] Kafka consumer reconnecting in 3s: {}", groupId, e.getMessage());
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private VoteRecord parseVote(String json) throws Exception {
        if (json == null || json.isBlank()) return null;
        JsonNode root = objectMapper.readTree(json);
        String voterId = root.has("voterId") && root.get("voterId").isObject()
            ? root.path("voterId").path("value").asText()
            : root.path("voterId").asText();
        String candidateId = root.has("candidateId") && root.get("candidateId").isObject()
            ? root.path("candidateId").path("value").asText()
            : root.path("candidateId").asText();
        String ip = root.has("ipAddress") && root.get("ipAddress").isObject()
            ? root.path("ipAddress").path("value").asText()
            : root.path("ipAddress").asText("127.0.0.1");
        String region = root.path("region").asText("CENTRO");
        String city = root.path("city").asText("Bogotá");
        String electionType = root.path("electionType").asText("NACIONAL");
        String voteSource = root.path("voteSource").asText("REAL");
        String ticketCode = root.path("ticketCode").asText("");
        String handledByNode = root.path("handledByNode").asText("voting-edge");
        Instant ts = root.has("timestamp") && !root.get("timestamp").isNull()
            ? parseInstant(root.get("timestamp"))
            : Instant.now();

        return VoteRecord.create(
            voterId, candidateId, city, region, electionType, voteSource, ip,
            ticketCode.isBlank() ? null : ticketCode, handledByNode, ts
        );
    }

    private Instant parseInstant(JsonNode node) {
        if (node.isNumber()) {
            double val = node.asDouble();
            long secs = (long) val;
            long nanos = (long) ((val - secs) * 1_000_000_000L);
            return Instant.ofEpochSecond(secs, nanos);
        }
        try {
            return Instant.parse(node.asText());
        } catch (Exception e) {
            return Instant.now();
        }
    }

    @PreDestroy
    public void shutdown() {
        running.set(false);
        executor.shutdownNow();
    }
}
