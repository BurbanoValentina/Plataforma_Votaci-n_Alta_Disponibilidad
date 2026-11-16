package com.voting.platform.dashboard.global.infra;

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
import com.voting.platform.dashboard.global.service.GlobalDashboardService;
import com.voting.platform.domain.model.GlobalResultRecord;
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
 * Consumer for RabbitMQ Fanout Exchange live_results_global.
 * Consumes periodic snapshots published by vote-processor and updates SSE connected web clients.
 */
@Component
public class RabbitMqGlobalConsumer {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqGlobalConsumer.class);

    private final GlobalDashboardService dashboardService;
    private final String rabbitHost;
    private final int rabbitPort;
    private final String fanoutExchange;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Connection rabbitConnection;
    private Channel rabbitChannel;
    private Thread listenerThread;

    public RabbitMqGlobalConsumer(
            GlobalDashboardService dashboardService,
            @Value("${RABBITMQ_HOST:localhost}") String rabbitHost,
            @Value("${RABBITMQ_PORT:5672}") int rabbitPort,
            @Value("${voting.rabbitmq.exchange.global:live_results_global}") String fanoutExchange) {
        this.dashboardService = dashboardService;
        this.rabbitHost = rabbitHost;
        this.rabbitPort = rabbitPort;
        this.fanoutExchange = fanoutExchange;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    @PostConstruct
    public void start() {
        running.set(true);
        listenerThread = new Thread(this::connectAndConsume, "rabbitmq-global-listener");
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
                rabbitChannel.exchangeDeclare(fanoutExchange, BuiltinExchangeType.FANOUT, true);
                String queueName = rabbitChannel.queueDeclare("", false, true, true, null).getQueue();
                rabbitChannel.queueBind(queueName, fanoutExchange, "");

                log.info("RabbitMQ Global Dashboard Worker subscribed to exchange: {}", fanoutExchange);

                rabbitChannel.basicConsume(queueName, true, new DefaultConsumer(rabbitChannel) {
                    @Override
                    public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) {
                        try {
                            String json = new String(body, StandardCharsets.UTF_8);
                            JsonNode node = objectMapper.readTree(json);
                            long total = node.path("totalVotes").asLong(0L);
                            Map<String, Long> candidateVotes = objectMapper.convertValue(
                                    node.path("candidateVotes"), new TypeReference<Map<String, Long>>() {});

                            GlobalResultRecord record = new GlobalResultRecord(candidateVotes, total, Instant.now());
                            dashboardService.updateResults(record);
                        } catch (Exception e) {
                            log.error("Failed to parse RabbitMQ global message: {}", e.getMessage());
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
