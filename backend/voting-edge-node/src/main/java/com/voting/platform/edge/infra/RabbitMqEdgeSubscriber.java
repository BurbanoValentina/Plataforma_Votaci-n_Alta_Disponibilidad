package com.voting.platform.edge.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import com.voting.platform.domain.model.GlobalResultRecord;
import com.voting.platform.domain.model.RegionalResultRecord;
import com.voting.platform.edge.service.DashboardBroadcastService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Per-node RabbitMQ Subscriber for horizontally scaled Edge Nodes.
 * Each AWS App Node creates its own exclusive auto-delete queues bound to:
 * - Fanout Exchange `live_results_global` -> guarantees EVERY edge node gets global updates!
 * - Topic Exchange `live_results_regional` (binding `results.*`) -> guarantees EVERY edge node gets regional updates!
 */
@Component
public class RabbitMqEdgeSubscriber {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqEdgeSubscriber.class);

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String globalExchange;
    private final String regionalExchange;
    private final String nodeId;
    private final DashboardBroadcastService broadcastService;
    private final ObjectMapper objectMapper;

    private final AtomicBoolean running = new AtomicBoolean(true);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile Connection connection;
    private volatile Channel channel;

    public RabbitMqEdgeSubscriber(
        @Value("${voting.rabbitmq.host:rabbitmq}") String host,
        @Value("${voting.rabbitmq.port:5672}") int port,
        @Value("${voting.rabbitmq.username:guest}") String username,
        @Value("${voting.rabbitmq.password:guest}") String password,
        @Value("${voting.rabbitmq.exchange.global:live_results_global}") String globalExchange,
        @Value("${voting.rabbitmq.exchange.regional:live_results_regional}") String regionalExchange,
        @Value("${voting.node-id:voting-edge-1}") String nodeId,
        DashboardBroadcastService broadcastService
    ) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.globalExchange = globalExchange;
        this.regionalExchange = regionalExchange;
        this.nodeId = nodeId;
        this.broadcastService = Objects.requireNonNull(broadcastService, "broadcastService cannot be null");
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    @PostConstruct
    public void start() {
        executor.submit(this::connectWithRetry);
    }

    private void connectWithRetry() {
        while (running.get()) {
            try {
                ConnectionFactory factory = new ConnectionFactory();
                factory.setHost(host);
                factory.setPort(port);
                factory.setUsername(username);
                factory.setPassword(password);
                factory.setAutomaticRecoveryEnabled(true);
                factory.setConnectionTimeout(4000);

                this.connection = factory.newConnection("edge-subscriber-" + nodeId);
                this.channel = connection.createChannel();

                // Declare Fanout & Topic exchanges
                channel.exchangeDeclare(globalExchange, BuiltinExchangeType.FANOUT, true);
                channel.exchangeDeclare(regionalExchange, BuiltinExchangeType.TOPIC, true);

                // Per-node exclusive auto-delete queues so BOTH AWS ALB nodes receive every message!
                String globalQueue = channel.queueDeclare("", false, true, true, null).getQueue();
                channel.queueBind(globalQueue, globalExchange, "");

                String regionalQueue = channel.queueDeclare("", false, true, true, null).getQueue();
                channel.queueBind(regionalQueue, regionalExchange, "results.*");

                DeliverCallback globalCb = (tag, delivery) -> {
                    try {
                        String json = new String(delivery.getBody(), StandardCharsets.UTF_8);
                        GlobalResultRecord record = objectMapper.readValue(json, GlobalResultRecord.class);
                        broadcastService.onGlobalResult(record);
                    } catch (Exception e) {
                        log.debug("[{}] Global message parse error: {}", nodeId, e.getMessage());
                    }
                };

                DeliverCallback regionalCb = (tag, delivery) -> {
                    try {
                        String json = new String(delivery.getBody(), StandardCharsets.UTF_8);
                        RegionalResultRecord record = objectMapper.readValue(json, RegionalResultRecord.class);
                        broadcastService.onRegionalResult(record);
                    } catch (Exception e) {
                        log.debug("[{}] Regional message parse error: {}", nodeId, e.getMessage());
                    }
                };

                channel.basicConsume(globalQueue, true, globalCb, tag -> {});
                channel.basicConsume(regionalQueue, true, regionalCb, tag -> {});

                log.info("[{}] RabbitMQ subscribed -> Fanout({}) & Topic({} [results.*])",
                    nodeId, globalExchange, regionalExchange);
                return;
            } catch (Exception e) {
                log.warn("[{}] RabbitMQ subscriber waiting for broker ({}:{}): {}", host, port, nodeId, e.getMessage());
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
    public void close() {
        running.set(false);
        executor.shutdownNow();
        try {
            if (channel != null && channel.isOpen()) channel.close();
            if (connection != null && connection.isOpen()) connection.close();
        } catch (Exception ignored) {
        }
    }
}
