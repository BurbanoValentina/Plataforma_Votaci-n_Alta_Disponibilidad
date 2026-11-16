package com.voting.platform.bot.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voting.platform.domain.model.SecurityAlertRecord;
import com.voting.platform.domain.port.SecurityAlertPublisher;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Adapter implementing SecurityAlertPublisher.
 * Publishes security alerts to Kafka topic security_alerts.
 */
@Component
public class LoggingSecurityAlertPublisher implements SecurityAlertPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingSecurityAlertPublisher.class);

    private final String topicName;
    private final ConcurrentLinkedQueue<SecurityAlertRecord> alertQueue;
    private final ObjectMapper objectMapper;
    private KafkaProducer<String, String> producer;

    public LoggingSecurityAlertPublisher(
            @Value("${voting.kafka.topic.security-alerts:security_alerts}") String topicName,
            @Value("${voting.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
        this.topicName = topicName;
        this.alertQueue = new ConcurrentLinkedQueue<>();
        this.objectMapper = new ObjectMapper().findAndRegisterModules();

        try {
            Properties props = new Properties();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            props.put(ProducerConfig.ACKS_CONFIG, "1");
            props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 3000);
            this.producer = new KafkaProducer<>(props);
            log.info("Kafka Producer for Security Alerts initialized for broker: {}", bootstrapServers);
        } catch (Exception e) {
            log.warn("Kafka broker unreachable for alerts publisher ({}), fallback in-memory: {}", bootstrapServers, e.getMessage());
            this.producer = null;
        }
    }

    @Override
    public void publishSecurityAlert(SecurityAlertRecord alert) {
        Objects.requireNonNull(alert, "alert cannot be null");
        alertQueue.add(alert);
        log.warn("[KAFKA TOPIC: {}] CRITICAL SECURITY ALERT: IP={}, Level={}, Reason={}",
            topicName, alert.ipAddress(), alert.riskLevel(), alert.reason());

        if (producer != null) {
            try {
                String key = alert.ipAddress().value();
                String json = objectMapper.writeValueAsString(alert);
                producer.send(new ProducerRecord<>(topicName, key, json));
            } catch (Exception e) {
                log.error("Failed to publish security alert to Kafka: {}", e.getMessage());
            }
        }
    }

    @PreDestroy
    public void close() {
        if (producer != null) {
            try {
                producer.close();
            } catch (Exception ignored) {
            }
        }
    }

    public int getAlertCount() {
        return alertQueue.size();
    }
}
