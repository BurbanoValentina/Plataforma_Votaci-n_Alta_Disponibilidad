package com.voting.platform.edge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voting.platform.domain.aggregate.RunoffPredictor;
import com.voting.platform.domain.model.ElectionType;
import com.voting.platform.domain.model.GlobalResultRecord;
import com.voting.platform.domain.model.RunoffPredictionRecord;
import com.voting.platform.domain.model.SecurityAlertRecord;
import com.voting.platform.domain.model.VoteTicketRecord;
import com.voting.platform.domain.port.NotificationPort;
import com.voting.platform.edge.infra.PostgresEdgeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Telegram Bot API Integration Service (implements DIP NotificationPort).
 * Ready to plug in any BotFather token & chatId via Admin Panel or Environment Variables.
 * Also processes incoming Telegram Webhook commands (/resultados, /segundavuelta, /ticket, /estado).
 */
@Service
public class TelegramNotificationService implements NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotificationService.class);

    private final PostgresEdgeRepository postgresRepository;
    private final DashboardBroadcastService broadcastService;
    private final String envBotToken;
    private final String envChatId;
    private final String nodeId;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public TelegramNotificationService(
        PostgresEdgeRepository postgresRepository,
        DashboardBroadcastService broadcastService,
        @Value("${TELEGRAM_BOT_TOKEN:}") String envBotToken,
        @Value("${TELEGRAM_CHAT_ID:}") String envChatId,
        @Value("${voting.node-id:voting-edge-1}") String nodeId
    ) {
        this.postgresRepository = Objects.requireNonNull(postgresRepository, "postgresRepository cannot be null");
        this.broadcastService = Objects.requireNonNull(broadcastService, "broadcastService cannot be null");
        this.envBotToken = envBotToken != null ? envBotToken.trim() : "";
        this.envChatId = envChatId != null ? envChatId.trim() : "";
        this.nodeId = nodeId;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    private record TelegramConfig(boolean enabled, String token, String chatId, boolean notifyVotes, boolean notifyFraud) {}

    private TelegramConfig resolveConfig() {
        Map<String, Object> db = postgresRepository.readTelegramRawConfig();
        String dbToken = db.get("telegram_bot_token") != null ? String.valueOf(db.get("telegram_bot_token")).trim() : "";
        String dbChat = db.get("telegram_chat_id") != null ? String.valueOf(db.get("telegram_chat_id")).trim() : "";
        boolean dbEnabled = Boolean.TRUE.equals(db.get("telegram_enabled"));
        boolean notifyVotes = !Boolean.FALSE.equals(db.get("telegram_notify_votes"));
        boolean notifyFraud = !Boolean.FALSE.equals(db.get("telegram_notify_fraud"));

        String token = !dbToken.isBlank() ? dbToken : envBotToken;
        String chatId = !dbChat.isBlank() ? dbChat : envChatId;
        boolean enabled = (dbEnabled || (!envBotToken.isBlank() && !envChatId.isBlank()))
            && !token.isBlank() && !chatId.isBlank();

        return new TelegramConfig(enabled, token, chatId, notifyVotes, notifyFraud);
    }

    @Override
    public void notifyRealVote(VoteTicketRecord ticket) {
        TelegramConfig cfg = resolveConfig();
        if (!cfg.enabled() || !cfg.notifyVotes()) return;

        String msg = String.format(
            "🗳️ <b>NUEVO VOTO REAL REGISTRADO</b>\n"
                + "• <b>Elección:</b> %s\n"
                + "• <b>Candidato:</b> %s\n"
                + "• <b>Ciudad / Región:</b> %s (%s)\n"
                + "• <b>Cédula:</b> <code>%s</code>\n"
                + "• <b>Ticket:</b> <code>%s</code>\n"
                + "• <b>Nodo AWS:</b> <code>%s</code>",
            ticket.electionType().getDisplayName(),
            escape(ticket.candidateName()),
            escape(ticket.city()),
            ticket.region().name(),
            escape(ticket.voterId()),
            escape(ticket.ticketCode()),
            escape(ticket.handledByNode())
        );
        CompletableFuture.runAsync(() -> sendMessage(cfg.token(), cfg.chatId(), msg));
    }

    @Override
    public void notifyFraudAlert(SecurityAlertRecord alert) {
        TelegramConfig cfg = resolveConfig();
        if (!cfg.enabled() || !cfg.notifyFraud()) return;

        String icon = "BLOQUEADA".equalsIgnoreCase(alert.riskLevel()) ? "🚨" : "⚠️";
        String msg = String.format(
            "%s <b>ALERTA ANTI-FRAUDE [%s]</b>\n"
                + "• <b>IP Origen:</b> <code>%s</code>\n"
                + "• <b>Intentos:</b> %d\n"
                + "• <b>Detalle:</b> %s\n"
                + "• <b>Nodo AWS:</b> <code>%s</code>",
            icon,
            escape(alert.riskLevel()),
            escape(alert.ipAddress().value()),
            alert.voteCountInWindow(),
            escape(alert.reason()),
            escape(nodeId)
        );
        CompletableFuture.runAsync(() -> sendMessage(cfg.token(), cfg.chatId(), msg));
    }

    @Override
    public void notifyLifecycleChange(String newState, String actorNode) {
        TelegramConfig cfg = resolveConfig();
        if (!cfg.enabled()) return;

        String msg = String.format(
            "🏛️ <b>CAMBIO DE ESTADO ELECTORAL (Patrón State)</b>\n"
                + "• <b>Nuevo Estado:</b> <b>%s</b>\n"
                + "• <b>Aplicado vía Nodo:</b> <code>%s</code>",
            escape(newState), escape(actorNode)
        );
        CompletableFuture.runAsync(() -> sendMessage(cfg.token(), cfg.chatId(), msg));
    }

    public Map<String, Object> sendTestMessage() {
        TelegramConfig cfg = resolveConfig();
        if (cfg.token().isBlank() || cfg.chatId().isBlank()) {
            return Map.of("ok", false, "error", "Debe configurar el Bot Token (de @BotFather) y el Chat ID primero.");
        }
        String msg = "✅ <b>Conexión Exitosa — Plataforma Electoral AWS</b>\n"
            + "El bot de Telegram quedó vinculado correctamente al clúster balanceado.\n"
            + "• <b>Nodo AWS:</b> <code>" + escape(nodeId) + "</code>\n"
            + "• Comandos disponibles vía Webhook: /resultados, /segundavuelta, /estado, /ticket &lt;cedula&gt;";
        return sendMessage(cfg.token(), cfg.chatId(), msg);
    }

    public Map<String, Object> sendElectoralSummary(String targetChatId) {
        TelegramConfig cfg = resolveConfig();
        String chat = (targetChatId != null && !targetChatId.isBlank()) ? targetChatId : cfg.chatId();
        if (cfg.token().isBlank() || chat.isBlank()) {
            return Map.of("ok", false, "error", "Bot Token o Chat ID no configurado.");
        }
        String summary = buildLiveSummaryText();
        return sendMessage(cfg.token(), chat, summary);
    }

    public String buildLiveSummaryText() {
        GlobalResultRecord g = broadcastService.getLatestGlobal();
        RunoffPredictionRecord nacPred = RunoffPredictor.predict(g, ElectionType.NACIONAL, "ALL");
        Map<String, Object> counters = postgresRepository.queryDatabaseCounters();

        StringBuilder sb = new StringBuilder();
        sb.append("📊 <b>REPORTE EN VIVO — PLATAFORMA ELECTORAL AWS</b>\n");
        sb.append(String.format("• Total votos: <b>%,d</b> (Reales: <b>%,d</b> | Simulados: <b>%,d</b>)\n",
            g.totalVotes(), g.totalRealVotes(), g.totalSimulatedVotes()));
        sb.append(String.format("• Alertas Anti-Fraude: <b>%s</b>\n\n", counters.getOrDefault("total_incidents", 0)));

        sb.append("🏛️ <b>PREDICCIÓN SEGUNDA VUELTA (NACIONAL)</b>\n");
        sb.append(escape(nacPred.verdictTitle())).append("\n");
        for (int i = 0; i < nacPred.finalists().size(); i++) {
            var f = nacPred.finalists().get(i);
            sb.append(String.format("%d) <b>%s</b>: %.2f%% (%,d votos | Prob. 2da vuelta: %.1f%%)\n",
                i + 1, escape(f.name()), f.currentSharePct(), f.votes(), f.winProbabilityPct()));
        }
        return sb.toString();
    }

    public Map<String, Object> handleWebhookUpdate(Map<String, Object> update) {
        if (update == null || !update.containsKey("message")) {
            return Map.of("ok", true, "ignored", true);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> message = (Map<String, Object>) update.get("message");
        @SuppressWarnings("unchecked")
        Map<String, Object> chat = message != null ? (Map<String, Object>) message.get("chat") : null;
        if (chat == null || chat.get("id") == null) {
            return Map.of("ok", true, "ignored", true);
        }
        String chatId = String.valueOf(chat.get("id"));
        String text = message.get("text") != null ? String.valueOf(message.get("text")).trim() : "";

        TelegramConfig cfg = resolveConfig();
        if (cfg.token().isBlank()) {
            return Map.of("ok", false, "error", "Telegram Bot Token not configured");
        }

        String reply;
        if (text.startsWith("/resultados") || text.startsWith("/segundavuelta")) {
            reply = buildLiveSummaryText();
        } else if (text.startsWith("/ticket")) {
            String[] parts = text.split("\\s+");
            if (parts.length < 2) {
                reply = "ℹ️ Usa el formato: <code>/ticket 1085999001</code>";
            } else {
                List<VoteTicketRecord> tickets = postgresRepository.findByVoter(com.voting.platform.domain.model.VoterId.of(parts[1]));
                if (tickets.isEmpty()) {
                    reply = "🔎 La cédula <code>" + escape(parts[1]) + "</code> aún no registra votos.";
                } else {
                    StringBuilder sb = new StringBuilder("🎟️ <b>TICKETS ENCONTRADOS</b>\n");
                    for (VoteTicketRecord t : tickets) {
                        sb.append(String.format("• <b>%s</b>: <code>%s</code> — %s (%s)\n",
                            t.electionType().name(), escape(t.ticketCode()), escape(t.candidateName()), escape(t.city())));
                    }
                    reply = sb.toString();
                }
            }
        } else {
            reply = "🏛️ <b>Bot Oficial — Plataforma de Votación AWS</b>\n"
                + "Comandos disponibles:\n"
                + "• /resultados — Resumen en vivo (Reales vs Simulados)\n"
                + "• /segundavuelta — Predicción de los 2 candidatos finalistas\n"
                + "• /ticket &lt;cedula&gt; — Verificar comprobante electoral";
        }

        return sendMessage(cfg.token(), chatId, reply);
    }

    public Map<String, Object> sendMessage(String botToken, String chatId, String htmlText) {
        try {
            String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("chat_id", chatId);
            payload.put("text", htmlText);
            payload.put("parse_mode", "HTML");

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            boolean success = resp.statusCode() >= 200 && resp.statusCode() < 300;
            if (!success) {
                log.warn("[{}] Telegram API returned HTTP {}: {}", nodeId, resp.statusCode(), resp.body());
            }
            return Map.of("ok", success, "statusCode", resp.statusCode(), "response", resp.body());
        } catch (Exception e) {
            log.warn("[{}] Telegram send failed: {}", nodeId, e.getMessage());
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    private String escape(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
