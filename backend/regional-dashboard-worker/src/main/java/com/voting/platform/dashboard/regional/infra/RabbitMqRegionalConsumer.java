package com.voting.platform.dashboard.regional.infra;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.voting.platform.dashboard.regional.service.RegionalDashboardService;
import com.voting.platform.domain.model.Region;
import com.voting.platform.domain.model.RegionalResultRecord;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Consumer for RabbitMQ Topic Exchange live_results_regional.
 * Subscribes to routing pattern 'results.*' and updates regional dashboard web clients.
 */
@Component
public class RabbitMqRegionalConsumer {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqRegionalConsumer.class);

    private final RegionalDashboardService dashboardService;
    private final String rabbitHost;
    private final int rabbitPort;
    private final String topicExchange;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Connection rabbitConnection;
    private Channel rabbitChannel;
    private Thread listenerThread;

    public RabbitMqRegionalConsumer(
            RegionalDashboardService dashboardService,
            @Value("${RABBITMQ_HOST:localhost}") String rabbitHost,
            @Value("${RABBITMQ_PORT:5672}") int rabbitPort,
            @Value("${voting.rabbitmq.exchange.regional:live_results_regional}") String topicExchange) {
        this.dashboardService = dashboardService;
        this.rabbitHost = rabbitHost;
        this.rabbitPort = rabbitPort;
        this.topicExchange = topicExchange;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    @PostConstruct
    public void start() {
        running.set(true);
        listenerThread = new Thread(this::connectAndConsume, "rabbitmq-regional-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    private void connectAndConsume() {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(rabbitHost);
        factory.setPort(rabbitPort);
        factory.setUsername("guest");
        factory.setPassword("guest");
        factory.setConnectionTimeout(3000);

        while (running.get() && rabbitConnection == null) {
            try {
                rabbitConnection = factory.newConnection();
                rabbitChannel = rabbitConnection.createChannel();
                rabbitChannel.exchangeDeclare(topicExchange, BuiltinExchangeType.TOPIC, true);
                String queueName = rabbitChannel.queueDeclare("", false, true, true, null).getQueue();
                rabbitChannel.queueBind(queueName, topicExchange, "results.*");

                log.info("RabbitMQ Regional Dashboard Worker subscribed to exchange: {} with key results.*", topicExchange);

                rabbitChannel.basicConsume(queueName, true, new DefaultConsumer(rabbitChannel) {
                    @Override
                    public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) {
                        try {
                            String json = new String(body, StandardCharsets.UTF_8);
                            JsonNode node = objectMapper.readTree(json);
                            String regionStr = node.path("region").asText("NORTE");
                            Region region = Region.valueOf(regionStr);
                            long total = node.path("totalRegionalVotes").asLong(0L);

                            Map<String, Long> rawVotes = objectMapper.convertValue(
                                    node.path("rawVotes"), new TypeReference<Map<String, Long>>() {});
                            Map<String, Double> weightedVotes = objectMapper.convertValue(
                                    node.path("weightedVotes"), new TypeReference<Map<String, Double>>() {});

                            RegionalResultRecord record = new RegionalResultRecord(region, weightedVotes, rawVotes, total, Instant.now());
                            dashboardService.updateRegionalResult(record);
                        } catch (Exception e) {
                            log.error("Failed to parse RabbitMQ regional message: {}", e.getMessage());
                        }
                    }
                });
            } catch (Exception e) {
                log.warn("Cannot connect to RabbitMQ ({}:{}). Retrying in 3 seconds: {}", rabbitHost, rabbitPort, e.getMessage());
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        try {
            if (rabbitChannel != null && rabbitChannel.isOpen()) rabbitChannel.close();
            if (rabbitConnection != null && rabbitConnection.isOpen()) rabbitConnection.close();
        } catch (Exception ignored) {
        }
    }
}
