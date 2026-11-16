package com.voting.platform.regional.infra;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.voting.platform.domain.model.RegionalResultRecord;
import com.voting.platform.domain.port.RegionalResultPublisher;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapter implementing RegionalResultPublisher.
 * Publishes regional snapshots to RabbitMQ Topic Exchange live_results_regional.
 */
@Component
public class LoggingRegionalResultPublisher implements RegionalResultPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingRegionalResultPublisher.class);

    private final String topicExchange;
    private final ConcurrentHashMap<String, RegionalResultRecord> latestByRoutingKey;
    private Connection rabbitConnection;
    private Channel rabbitChannel;

    public LoggingRegionalResultPublisher(
            @Value("${voting.rabbitmq.exchange.regional:live_results_regional}") String topicExchange,
            @Value("${RABBITMQ_HOST:localhost}") String rabbitHost,
            @Value("${RABBITMQ_PORT:5672}") int rabbitPort) {
        this.topicExchange = topicExchange;
        this.latestByRoutingKey = new ConcurrentHashMap<>();

        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(rabbitHost);
            factory.setPort(rabbitPort);
            factory.setUsername("guest");
            factory.setPassword("guest");
            factory.setConnectionTimeout(3000);
            this.rabbitConnection = factory.newConnection();
            this.rabbitChannel = rabbitConnection.createChannel();
            this.rabbitChannel.exchangeDeclare(topicExchange, BuiltinExchangeType.TOPIC, true);
            log.info("RabbitMQ connection established for Topic Exchange: {}", topicExchange);
        } catch (Exception e) {
            log.warn("RabbitMQ broker unreachable at startup ({}:{}), using in-memory mode: {}",
                    rabbitHost, rabbitPort, e.getMessage());
            this.rabbitConnection = null;
            this.rabbitChannel = null;
        }
    }

    @Override
    public void publishRegionalResults(String routingKey, RegionalResultRecord results) {
        Objects.requireNonNull(routingKey, "routingKey cannot be null");
        Objects.requireNonNull(results, "results cannot be null");
        latestByRoutingKey.put(routingKey, results);
        log.info("[RABBITMQ TOPIC EXCHANGE: {}] Published to routingKey: '{}' -> Total: {}, Weighted: {}",
            topicExchange, routingKey, results.totalRegionalVotes(), results.weightedVotes());

        if (rabbitChannel != null && rabbitChannel.isOpen()) {
            try {
                StringBuilder sb = new StringBuilder("{");
                sb.append("\"region\":\"").append(results.region().name()).append("\",");
                sb.append("\"totalRegionalVotes\":").append(results.totalRegionalVotes()).append(",");
                sb.append("\"generatedAt\":\"").append(results.generatedAt().toString()).append("\",");
                sb.append("\"rawVotes\":{");
                boolean first = true;
                for (Map.Entry<String, Long> entry : results.rawVotes().entrySet()) {
                    if (!first) sb.append(",");
                    sb.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
                    first = false;
                }
                sb.append("},\"weightedVotes\":{");
                first = true;
                for (Map.Entry<String, Double> entry : results.weightedVotes().entrySet()) {
                    if (!first) sb.append(",");
                    sb.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
                    first = false;
                }
                sb.append("}}");
                String json = sb.toString();

                rabbitChannel.basicPublish(topicExchange, routingKey, null, json.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                log.error("Failed to publish regional results to RabbitMQ: {}", e.getMessage());
            }
        }
    }

    @PreDestroy
    public void close() {
        try {
            if (rabbitChannel != null && rabbitChannel.isOpen()) rabbitChannel.close();
            if (rabbitConnection != null && rabbitConnection.isOpen()) rabbitConnection.close();
        } catch (Exception ignored) {
        }
    }

    public RegionalResultRecord getLatest(String routingKey) {
        return latestByRoutingKey.get(routingKey);
    }
}
