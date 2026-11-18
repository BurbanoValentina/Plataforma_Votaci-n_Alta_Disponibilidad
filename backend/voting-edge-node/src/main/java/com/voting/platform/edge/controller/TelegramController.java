package com.voting.platform.edge.controller;

import com.voting.platform.edge.infra.PostgresEdgeRepository;
import com.voting.platform.edge.service.TelegramNotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Telegram Bot API Management, Interactive Long-Polling & Activity Feed Controller.
 */
@RestController
@RequestMapping("/api/telegram")
public class TelegramController {

    private final PostgresEdgeRepository postgresRepository;
    private final TelegramNotificationService telegramService;
    private final String nodeId;

    public TelegramController(
        PostgresEdgeRepository postgresRepository,
        TelegramNotificationService telegramService,
        @Value("${voting.node-id:voting-edge-1}") String nodeId
    ) {
        this.postgresRepository = Objects.requireNonNull(postgresRepository, "postgresRepository cannot be null");
        this.telegramService = Objects.requireNonNull(telegramService, "telegramService cannot be null");
        this.nodeId = nodeId;
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        Map<String, Object> ctrl = postgresRepository.readControlPanel();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("servedByNode", nodeId);
        body.put("enabled", ctrl.getOrDefault("telegram_enabled", false));
        body.put("tokenConfigured", ctrl.getOrDefault("telegram_token_configured", false));
        body.put("chatId", ctrl.getOrDefault("telegram_chat_id", ""));
        body.put("notifyVotes", ctrl.getOrDefault("telegram_notify_votes", true));
        body.put("notifyFraud", ctrl.getOrDefault("telegram_notify_fraud", true));
        body.put("pollingMode", true);
        body.put("webhookPath", "/api/telegram/webhook");
        return ResponseEntity.ok().header("X-Node-Id", nodeId).body(body);
    }

    @PostMapping("/config")
    public ResponseEntity<Map<String, Object>> updateConfig(@RequestBody Map<String, Object> payload) {
        Boolean enabled = payload.containsKey("enabled")
            ? Boolean.parseBoolean(String.valueOf(payload.get("enabled"))) : null;
        String botToken = payload.containsKey("botToken")
            ? String.valueOf(payload.get("botToken")) : null;
        String chatId = payload.containsKey("chatId")
            ? String.valueOf(payload.get("chatId")) : null;
        Boolean notifyVotes = payload.containsKey("notifyVotes")
            ? Boolean.parseBoolean(String.valueOf(payload.get("notifyVotes"))) : null;
        Boolean notifyFraud = payload.containsKey("notifyFraud")
            ? Boolean.parseBoolean(String.valueOf(payload.get("notifyFraud"))) : null;

        postgresRepository.updateTelegramConfig(enabled, botToken, chatId, notifyVotes, notifyFraud);
        return getConfig();
    }

    @GetMapping("/activity")
    public ResponseEntity<Map<String, Object>> getActivity(@RequestParam(defaultValue = "20") int limit) {
        List<Map<String, Object>> events = postgresRepository.queryTelegramActivity(limit);
        return ResponseEntity.ok().header("X-Node-Id", nodeId).body(Map.of("events", events, "servedByNode", nodeId));
    }

    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> sendTest() {
        return ResponseEntity.ok().header("X-Node-Id", nodeId).body(telegramService.sendTestMessage());
    }

    @PostMapping("/send-summary")
    public ResponseEntity<Map<String, Object>> sendSummary(@RequestBody(required = false) Map<String, Object> payload) {
        String customChat = payload != null && payload.get("chatId") != null ? String.valueOf(payload.get("chatId")) : null;
        return ResponseEntity.ok().header("X-Node-Id", nodeId).body(telegramService.sendElectoralSummary(customChat));
    }

    @PostMapping("/send-runoff")
    public ResponseEntity<Map<String, Object>> sendRunoff(@RequestBody(required = false) Map<String, Object> payload) {
        String customChat = payload != null && payload.get("chatId") != null ? String.valueOf(payload.get("chatId")) : null;
        return ResponseEntity.ok().header("X-Node-Id", nodeId).body(telegramService.sendRunoffReport(customChat));
    }

    @PostMapping("/send-nodes")
    public ResponseEntity<Map<String, Object>> sendNodes(@RequestBody(required = false) Map<String, Object> payload) {
        String customChat = payload != null && payload.get("chatId") != null ? String.valueOf(payload.get("chatId")) : null;
        return ResponseEntity.ok().header("X-Node-Id", nodeId).body(telegramService.sendNodesReport(customChat));
    }

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> webhook(@RequestBody Map<String, Object> update) {
        return ResponseEntity.ok().header("X-Node-Id", nodeId).body(telegramService.handleWebhookUpdate(update));
    }
}
