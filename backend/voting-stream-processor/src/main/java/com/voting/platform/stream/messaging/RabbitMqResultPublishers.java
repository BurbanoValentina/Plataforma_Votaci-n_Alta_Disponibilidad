package com.voting.platform.stream.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.voting.platform.domain.model.GlobalResultRecord;
import com.voting.platform.domain.model.RegionalResultRecord;
import com.voting.platform.domain.port.GlobalResultPublisher;
import com.voting.platform.domain.port.RegionalResultPublisher;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * RabbitMQ Publisher Adapter implementing both:
 * - GlobalResultPublisher -> Fanout Exchange (live_results_global)
 * - RegionalResultPublisher -> Topic Exchange (live_results_regional, routingKey = results.<region>)
 */
@Component
public class RabbitMqResultPublishers implements GlobalResultPublisher, RegionalResultPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqResultPublishers.class);

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String globalExchange;
    private final String regionalExchange;
    private final ObjectMapper objectMapper;

    private volatile Connection connection;
    private volatile Channel channel;

    public RabbitMqResultPublishers(
        @Value("${voting.rabbitmq.host:rabbitmq}") String host,
        @Value("${voting.rabbitmq.port:5672}") int port,
        @Value("${voting.rabbitmq.username:guest}") String username,
        @Value("${voting.rabbitmq.password:guest}") String password,
        @Value("${voting.rabbitmq.exchange.global:live_results_global}") String globalExchange,
        @Value("${voting.rabbitmq.exchange.regional:live_results_regional}") String regionalExchange
    ) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.globalExchange = globalExchange;
        this.regionalExchange = regionalExchange;
        this.objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        ensureConnected();
    }

    private synchronized Channel ensureConnected() {
        if (channel != null && channel.isOpen()) {
            return channel;
        }
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(host);
            factory.setPort(port);
            factory.setUsername(username);
            factory.setPassword(password);
            factory.setAutomaticRecoveryEnabled(true);
            factory.setConnectionTimeout(3000);
            this.connection = factory.newConnection("voting-stream-processor");
            this.channel = connection.createChannel();
            this.channel.exchangeDeclare(globalExchange, BuiltinExchangeType.FANOUT, true);
            this.channel.exchangeDeclare(regionalExchange, BuiltinExchangeType.TOPIC, true);
            log.info("RabbitMQ connected ({}:{}) -> Fanout={}, Topic={}", host, port, globalExchange, regionalExchange);
        } catch (Exception e) {
            log.warn("RabbitMQ not ready yet ({}:{}): {}", host, port, e.getMessage());
            this.channel = null;
        }
        return channel;
    }

    @Override
    public void publishGlobalResults(GlobalResultRecord results) {
        Objects.requireNonNull(results, "results cannot be null");
        Channel ch = ensureConnected();
        if (ch == null) return;
        try {
            byte[] body = objectMapper.writeValueAsString(results).getBytes(StandardCharsets.UTF_8);
            synchronized (this) {
                ch.basicPublish(globalExchange, "", null, body);
            }
        } catch (Exception e) {
            log.warn("Failed publishing global snapshot to RabbitMQ: {}", e.getMessage());
            this.channel = null;
        }
    }

    @Override
    public void publishRegionalResults(String routingKey, RegionalResultRecord results) {
        Objects.requireNonNull(routingKey, "routingKey cannot be null");
        Objects.requireNonNull(results, "results cannot be null");
        Channel ch = ensureConnected();
        if (ch == null) return;
        try {
            byte[] body = objectMapper.writeValueAsString(results).getBytes(StandardCharsets.UTF_8);
            synchronized (this) {
                ch.basicPublish(regionalExchange, routingKey, null, body);
            }
        } catch (Exception e) {
            log.warn("Failed publishing regional snapshot ({}) to RabbitMQ: {}", routingKey, e.getMessage());
            this.channel = null;
        }
    }

    @PreDestroy
    public void close() {
        try {
            if (channel != null && channel.isOpen()) channel.close();
            if (connection != null && connection.isOpen()) connection.close();
        } catch (Exception ignored) {
        }
    }
}
