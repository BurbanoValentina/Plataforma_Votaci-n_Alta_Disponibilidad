package com.voting.platform.processor.infra;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.voting.platform.domain.model.GlobalResultRecord;
import com.voting.platform.domain.port.GlobalResultPublisher;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Adapter implementation of GlobalResultPublisher.
 * Publishes snapshot to RabbitMQ Fanout Exchange live_results_global.
 */
@Component
public class LoggingGlobalResultPublisher implements GlobalResultPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingGlobalResultPublisher.class);

    private final String fanoutExchange;
    private final AtomicReference<GlobalResultRecord> latestSnapshot;
    private Connection rabbitConnection;
    private Channel rabbitChannel;

    public LoggingGlobalResultPublisher(
            @Value("${voting.rabbitmq.exchange.global:live_results_global}") String fanoutExchange,
            @Value("${RABBITMQ_HOST:localhost}") String rabbitHost,
            @Value("${RABBITMQ_PORT:5672}") int rabbitPort) {
        this.fanoutExchange = fanoutExchange;
        this.latestSnapshot = new AtomicReference<>();

        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(rabbitHost);
            factory.setPort(rabbitPort);
            factory.setUsername("guest");
            factory.setPassword("guest");
            factory.setConnectionTimeout(3000);
            this.rabbitConnection = factory.newConnection();
            this.rabbitChannel = rabbitConnection.createChannel();
            this.rabbitChannel.exchangeDeclare(fanoutExchange, BuiltinExchangeType.FANOUT, true);
            log.info("RabbitMQ connection established for Fanout Exchange: {}", fanoutExchange);
        } catch (Exception e) {
            log.warn("RabbitMQ broker not reachable at startup ({}:{}), using in-memory mode: {}",
                    rabbitHost, rabbitPort, e.getMessage());
            this.rabbitConnection = null;
            this.rabbitChannel = null;
        }
    }

    @Override
    public void publishGlobalResults(GlobalResultRecord results) {
        Objects.requireNonNull(results, "results cannot be null");
        latestSnapshot.set(results);
        log.info("[RABBITMQ FANOUT EXCHANGE: {}] Broadcast: total={}, candidates={}",
            fanoutExchange, results.totalVotes(), results.candidateVotes());

        if (rabbitChannel != null && rabbitChannel.isOpen()) {
            try {
                StringBuilder sb = new StringBuilder("{");
                sb.append("\"totalVotes\":").append(results.totalVotes()).append(",");
                sb.append("\"generatedAt\":\"").append(results.generatedAt().toString()).append("\",");
                sb.append("\"candidateVotes\":{");
                boolean first = true;
                for (Map.Entry<String, Long> entry : results.candidateVotes().entrySet()) {
                    if (!first) sb.append(",");
                    sb.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
                    first = false;
                }
                sb.append("}}");
                String json = sb.toString();

                rabbitChannel.basicPublish(fanoutExchange, "", null, json.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                log.error("Failed to publish global results to RabbitMQ: {}", e.getMessage());
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

    public GlobalResultRecord getLatestSnapshot() {
        return latestSnapshot.get();
    }
}
