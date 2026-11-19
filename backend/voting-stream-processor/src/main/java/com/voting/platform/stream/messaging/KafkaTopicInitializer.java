package com.voting.platform.stream.messaging;

import jakarta.annotation.PostConstruct;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Programmatically initializes compacted Kafka topics (eligible_voters, raw_votes)
 * and security_alerts via Kafka AdminClient, eliminating the need for a separate kafka-init container.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class KafkaTopicInitializer {

    private static final Logger log = LoggerFactory.getLogger(KafkaTopicInitializer.class);

    private final String bootstrapServers;

    public KafkaTopicInitializer(@Value("${voting.kafka.bootstrap-servers:kafka:9092}") String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    @PostConstruct
    public void ensureTopics() {
        for (int attempt = 1; attempt <= 6; attempt++) {
            Properties props = new Properties();
            props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 4000);
            try (AdminClient admin = AdminClient.create(props)) {
                NewTopic eligible = new NewTopic("eligible_voters", 3, (short) 1).configs(Map.of(
                    TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_COMPACT,
                    TopicConfig.MIN_CLEANABLE_DIRTY_RATIO_CONFIG, "0.01"
                ));
                NewTopic rawVotes = new NewTopic("raw_votes", 3, (short) 1).configs(Map.of(
                    TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_COMPACT,
                    TopicConfig.MIN_CLEANABLE_DIRTY_RATIO_CONFIG, "0.01"
                ));
                NewTopic alerts = new NewTopic("security_alerts", 3, (short) 1);

                admin.createTopics(List.of(eligible, rawVotes, alerts)).all().get(8, TimeUnit.SECONDS);
                log.info("Kafka topics verified/created: eligible_voters (compact), raw_votes (compact), security_alerts");
                return;
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("TopicExistsException")) {
                    log.info("Kafka topics already exist on {}", bootstrapServers);
                    return;
                }
                log.warn("Kafka topic init attempt {}/6 deferred: {}", attempt, e.getMessage());
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
