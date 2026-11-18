package com.voting.platform.edge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voting.platform.domain.aggregate.RunoffPredictor;
import com.voting.platform.domain.model.CandidateCatalog;
import com.voting.platform.domain.model.City;
import com.voting.platform.domain.model.ElectionType;
import com.voting.platform.domain.model.GlobalResultRecord;
import com.voting.platform.domain.model.RunoffPredictionRecord;
import com.voting.platform.domain.model.SecurityAlertRecord;
import com.voting.platform.domain.model.VoteRecord;
import com.voting.platform.domain.model.VoteSource;
import com.voting.platform.domain.model.VoteTicketCode;
import com.voting.platform.domain.model.VoteTicketRecord;
import com.voting.platform.domain.model.VoterId;
import com.voting.platform.domain.port.NotificationPort;
import com.voting.platform.edge.infra.KafkaEdgeClient;
import com.voting.platform.edge.infra.PostgresEdgeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Full Two-Way Telegram Bot API Integration (Long-Polling + Webhook + Inline Keyboard).
 * Works immediately over HTTP AWS ALB with ZERO SSL/HTTPS setup required!
 * Allows a professor or administrator to control the entire electoral platform from Telegram:
 * - Request 2nd-Round Prediction Report
 * - Request AWS Load Balancer Node Distribution Report
 * - Change Electoral Lifecycle (HABILITADO / EN_AUDITORIA / CERRADO)
 * - Launch real-time simulation bursts (+25 votes), toggle simulator ON/OFF, or trigger Bot Attack
 * - Every Telegram action is logged in PostgreSQL telegram_activity_log and displayed live in Admin Panel!
 */
@Service
public class TelegramNotificationService implements NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotificationService.class);

    private final PostgresEdgeRepository postgresRepository;
    private final DashboardBroadcastService broadcastService;
    private final KafkaEdgeClient kafkaEdgeClient;
    private final String envBotToken;
    private final String envChatId;
    private final String nodeId;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public TelegramNotificationService(
        PostgresEdgeRepository postgresRepository,
        DashboardBroadcastService broadcastService,
        KafkaEdgeClient kafkaEdgeClient,
        @Value("${TELEGRAM_BOT_TOKEN:}") String envBotToken,
        @Value("${TELEGRAM_CHAT_ID:}") String envChatId,
        @Value("${voting.node-id:voting-edge-1}") String nodeId
    ) {
        this.postgresRepository = Objects.requireNonNull(postgresRepository, "postgresRepository cannot be null");
        this.broadcastService = Objects.requireNonNull(broadcastService, "broadcastService cannot be null");
        this.kafkaEdgeClient = Objects.requireNonNull(kafkaEdgeClient, "kafkaEdgeClient cannot be null");
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
        boolean enabled = (dbEnabled || (!envBotToken.isBlank() && !envChatId.isBlank())) && !token.isBlank();

        return new TelegramConfig(enabled, token, chatId, notifyVotes, notifyFraud);
    }

    /**
     * Distributed-safe Long-Polling loop (PostgreSQL advisory lock ensures only 1 AWS node polls at a time).
     * Automatically receives commands & button clicks from Telegram without needing HTTPS webhook!
     */
    @Scheduled(fixedDelay = 2500)
    public void pollTelegramUpdates() {
        TelegramConfig cfg = resolveConfig();
        if (!cfg.enabled() || cfg.token().isBlank()) return;
        if (!postgresRepository.tryClaimTelegramPollSlot()) return;

        try {
            long lastId = postgresRepository.getTelegramLastUpdateId();
            String url = "https://api.telegram.org/bot" + cfg.token()
                + "/getUpdates?timeout=1&limit=10" + (lastId > 0 ? "&offset=" + (lastId + 1) : "");

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(6))
                .GET()
                .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return;

            JsonNode root = objectMapper.readTree(resp.body());
            if (!root.path("ok").asBoolean(false)) return;
            JsonNode results = root.path("result");
            if (!results.isArray()) return;

            for (JsonNode upd : results) {
                long updateId = upd.path("update_id").asLong(0L);
                if (updateId > 0) {
                    postgresRepository.updateTelegramLastUpdateId(updateId);
                }
                processUpdateJson(cfg.token(), upd);
            }
        } catch (Exception e) {
            log.debug("[{}] Telegram poll skipped: {}", nodeId, e.getMessage());
        } finally {
            postgresRepository.releaseTelegramPollSlot();
        }
    }

    private void processUpdateJson(String token, JsonNode upd) {
        String chatId = null;
        String actor = "Usuario Telegram";
        String command = null;
        String callbackId = null;

        if (upd.has("callback_query")) {
            JsonNode cq = upd.get("callback_query");
            callbackId = cq.path("id").asText();
            command = cq.path("data").asText("");
            chatId = cq.path("message").path("chat").path("id").asText("");
            actor = resolveActor(cq.path("from"));
        } else if (upd.has("message")) {
            JsonNode msg = upd.get("message");
            command = msg.path("text").asText("").trim();
            chatId = msg.path("chat").path("id").asText("");
            actor = resolveActor(msg.path("from"));
        }

        if (chatId == null || chatId.isBlank() || command == null || command.isBlank()) return;

        // Auto-link Chat ID on first interaction so the user never has to search for their numeric Chat ID!
        TelegramConfig currentCfg = resolveConfig();
        if (currentCfg.chatId().isBlank()) {
            postgresRepository.updateTelegramConfig(true, null, chatId, null, null);
        }

        if (callbackId != null && !callbackId.isBlank()) {
            answerCallbackQuery(token, callbackId);
        }

        executeCommand(token, chatId, actor, command);
    }

    private String resolveActor(JsonNode from) {
        if (from == null || from.isMissingNode()) return "Usuario Telegram";
        String first = from.path("first_name").asText("");
        String username = from.path("username").asText("");
        if (!first.isBlank() && !username.isBlank()) return first + " (@" + username + ")";
        if (!username.isBlank()) return "@" + username;
        return !first.isBlank() ? first : "Usuario Telegram";
    }

    private void executeCommand(String token, String chatId, String actor, String rawCmd) {
        String cmd = rawCmd.trim().toLowerCase();

        if (cmd.startsWith("/segundavuelta") || cmd.equals("cmd_runoff")) {
            postgresRepository.recordTelegramActivity(actor, "INFORME_2DA_VUELTA",
                "El usuario de Telegram (" + actor + ") solicitó el Informe de Segunda Vuelta.");
            sendMessageWithMenu(token, chatId, buildRunoffReportText());
        } else if (cmd.startsWith("/nodos") || cmd.equals("cmd_nodes")) {
            postgresRepository.recordTelegramActivity(actor, "INFORME_NODOS_AWS",
                "El usuario de Telegram (" + actor + ") solicitó el Informe de Distribución de Carga de Nodos AWS.");
            sendMessageWithMenu(token, chatId, buildNodesReportText());
        } else if (cmd.startsWith("/resultados") || cmd.equals("cmd_summary")) {
            postgresRepository.recordTelegramActivity(actor, "INFORME_GENERAL",
                "El usuario de Telegram (" + actor + ") solicitó el Resumen Electoral en Vivo.");
            sendMessageWithMenu(token, chatId, buildLiveSummaryText());
        } else if (cmd.startsWith("/simular") || cmd.equals("cmd_burst")) {
            dispatchSimulatedBurst(25);
            postgresRepository.recordTelegramActivity(actor, "SIMULACION_RAFAGA",
                "El usuario de Telegram (" + actor + ") lanzó una simulación en tiempo real de +25 votos.");
            sendMessageWithMenu(token, chatId,
                "⚡ <b>Simulación ejecutada desde Telegram</b>\nSe despacharon <b>+25 votos simulados</b> hacia Kafka y RabbitMQ desde <code>" + escape(nodeId) + "</code>.");
        } else if (cmd.startsWith("/sim_on") || cmd.equals("cmd_sim_on")) {
            postgresRepository.updateSimulatorSettings(true, null, null, null);
            postgresRepository.recordTelegramActivity(actor, "SIMULADOR_ACTIVO",
                "El usuario de Telegram (" + actor + ") activó el Simulador Continuo en Tiempo Real.");
            sendMessageWithMenu(token, chatId, "▶️ <b>Simulador Continuo ACTIVADO</b> desde Telegram.");
        } else if (cmd.startsWith("/sim_off") || cmd.equals("cmd_sim_off")) {
            postgresRepository.updateSimulatorSettings(false, null, null, null);
            postgresRepository.recordTelegramActivity(actor, "SIMULADOR_PAUSADO",
                "El usuario de Telegram (" + actor + ") pausó el Simulador Continuo.");
            sendMessageWithMenu(token, chatId, "⏸️ <b>Simulador Continuo PAUSADO</b> desde Telegram.");
        } else if (cmd.startsWith("/bot") || cmd.equals("cmd_bot")) {
            postgresRepository.updateSimulatorSettings(null, null, null, true);
            postgresRepository.recordTelegramActivity(actor, "ATAQUE_BOT",
                "El usuario de Telegram (" + actor + ") disparó una simulación de Ataque Bot Anti-Fraude.");
            sendMessageWithMenu(token, chatId,
                "🛡️ <b>Simulación de Ataque Bot activada</b>\nSe enviarán 18 votos rápidos desde una misma IP para demostrar las transiciones NORMAL → SOSPECHOSA → BLOQUEADA.");
        } else if (cmd.startsWith("/estado_habilitado") || cmd.equals("cmd_state_habilitado")) {
            postgresRepository.updateLifecycleStateName("HABILITADO");
            postgresRepository.recordTelegramActivity(actor, "ESTADO_HABILITADO",
                "El usuario de Telegram (" + actor + ") HABILITÓ el Control Electoral (HABILITADO).");
            sendMessageWithMenu(token, chatId, "🟢 <b>Control Electoral cambiado a HABILITADO</b> (Patrón GoF State). Las urnas están abiertas.");
        } else if (cmd.startsWith("/estado_auditoria") || cmd.equals("cmd_state_auditoria")) {
            postgresRepository.updateLifecycleStateName("EN_AUDITORIA");
            postgresRepository.recordTelegramActivity(actor, "ESTADO_AUDITORIA",
                "El usuario de Telegram (" + actor + ") puso el Control Electoral EN AUDITORÍA (EN_AUDITORIA).");
            sendMessageWithMenu(token, chatId, "🟡 <b>Control Electoral cambiado a EN_AUDITORIA</b> (Patrón GoF State). Votación pausada para auditoría.");
        } else if (cmd.startsWith("/estado_cerrado") || cmd.equals("cmd_state_cerrado")) {
            postgresRepository.updateLifecycleStateName("CERRADO");
            postgresRepository.recordTelegramActivity(actor, "ESTADO_CERRADO",
                "El usuario de Telegram (" + actor + ") CERRÓ el Control Electoral (CERRADO).");
            sendMessageWithMenu(token, chatId, "🔴 <b>Control Electoral CERRADO</b> (Patrón GoF State). No se permiten más votos en los nodos AWS.");
        } else if (cmd.startsWith("/ticket")) {
            String[] parts = rawCmd.trim().split("\\s+");
            if (parts.length < 2) {
                sendMessageWithMenu(token, chatId, "ℹ️ Usa el formato: <code>/ticket 1138504215</code>");
            } else {
                postgresRepository.recordTelegramActivity(actor, "CONSULTA_TICKET",
                    "El usuario de Telegram (" + actor + ") consultó el ticket de la cédula " + parts[1] + ".");
                List<VoteTicketRecord> tickets = postgresRepository.findByVoter(VoterId.of(parts[1]));
                if (tickets.isEmpty()) {
                    sendMessageWithMenu(token, chatId, "🔎 La cédula <code>" + escape(parts[1]) + "</code> aún no registra votos.");
                } else {
                    StringBuilder sb = new StringBuilder("🎟️ <b>TICKETS ENCONTRADOS</b>\n");
                    for (VoteTicketRecord t : tickets) {
                        sb.append(String.format("• <b>%s</b>: <code>%s</code> — %s (%s)\n",
                            t.electionType().name(), escape(t.ticketCode()), escape(t.candidateName()), escape(t.city())));
                    }
                    sendMessageWithMenu(token, chatId, sb.toString());
                }
            }
        } else {
            postgresRepository.recordTelegramActivity(actor, "MENU_ABIERTO",
                "El usuario de Telegram (" + actor + ") abrió el Menú Interactivo de Control Electoral.");
            sendMessageWithMenu(token, chatId,
                "🏛️ <b>Centro de Control Electoral — Bot Oficial AWS</b>\n"
                    + "Usa los botones interactivos de abajo para pedir informes o controlar el clúster en tiempo real:");
        }
    }

    private void dispatchSimulatedBurst(int count) {
        List<String> cities = City.allCityNames();
        List<String> nacPool = CandidateCatalog.byElectionType(ElectionType.NACIONAL).stream()
            .map(CandidateCatalog.CandidateProfile::id).toList();
        List<String> regPool = CandidateCatalog.byElectionType(ElectionType.REGIONAL).stream()
            .map(CandidateCatalog.CandidateProfile::id).toList();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            ElectionType type = (i % 2 == 0) ? ElectionType.NACIONAL : ElectionType.REGIONAL;
            List<String> pool = type == ElectionType.NACIONAL ? nacPool : regPool;
            VoteRecord vote = VoteRecord.create(
                "TG-SIM-" + System.currentTimeMillis() + "-" + i,
                pool.get(rnd.nextInt(pool.size())),
                cities.get(rnd.nextInt(cities.size())),
                null, type.name(), VoteSource.SIMULATED.name(),
                "172.18." + rnd.nextInt(1, 200) + "." + rnd.nextInt(1, 250),
                VoteTicketCode.generate(type).value(),
                nodeId, Instant.now()
            );
            kafkaEdgeClient.publishRawVote(vote);
            postgresRepository.incrementNodeRequests(true);
        }
    }

    @Override
    public void notifyRealVote(VoteTicketRecord ticket) {
        TelegramConfig cfg = resolveConfig();
        if (!cfg.enabled() || cfg.chatId().isBlank() || !cfg.notifyVotes()) return;
        String msg = String.format(
            "🗳️ <b>NUEVO VOTO REAL REGISTRADO</b>\n"
                + "• <b>Elección:</b> %s\n"
                + "• <b>Candidato:</b> %s\n"
                + "• <b>Ciudad / Región:</b> %s (%s)\n"
                + "• <b>Cédula:</b> <code>%s</code>\n"
                + "• <b>Ticket:</b> <code>%s</code>\n"
                + "• <b>Nodo AWS:</b> <code>%s</code>",
            ticket.electionType().getDisplayName(), escape(ticket.candidateName()),
            escape(ticket.city()), ticket.region().name(), escape(ticket.voterId()),
            escape(ticket.ticketCode()), escape(ticket.handledByNode())
        );
        CompletableFuture.runAsync(() -> sendMessage(cfg.token(), cfg.chatId(), msg, false));
    }

    @Override
    public void notifyFraudAlert(SecurityAlertRecord alert) {
        TelegramConfig cfg = resolveConfig();
        if (!cfg.enabled() || cfg.chatId().isBlank() || !cfg.notifyFraud()) return;
        String icon = "BLOQUEADA".equalsIgnoreCase(alert.riskLevel()) ? "🚨" : "⚠️";
        String msg = String.format(
            "%s <b>ALERTA ANTI-FRAUDE [%s]</b>\n"
                + "• <b>IP Origen:</b> <code>%s</code>\n"
                + "• <b>Intentos:</b> %d\n"
                + "• <b>Detalle:</b> %s\n"
                + "• <b>Nodo AWS:</b> <code>%s</code>",
            icon, escape(alert.riskLevel()), escape(alert.ipAddress().value()),
            alert.voteCountInWindow(), escape(alert.reason()), escape(nodeId)
        );
        CompletableFuture.runAsync(() -> sendMessage(cfg.token(), cfg.chatId(), msg, false));
    }

    @Override
    public void notifyLifecycleChange(String newState, String actorNode) {
        TelegramConfig cfg = resolveConfig();
        if (!cfg.enabled() || cfg.chatId().isBlank()) return;
        String msg = String.format(
            "🏛️ <b>CAMBIO DE ESTADO ELECTORAL (Patrón State)</b>\n• <b>Nuevo Estado:</b> <b>%s</b>\n• <b>Nodo AWS:</b> <code>%s</code>",
            escape(newState), escape(actorNode)
        );
        CompletableFuture.runAsync(() -> sendMessage(cfg.token(), cfg.chatId(), msg, false));
    }

    public Map<String, Object> sendTestMessage() {
        TelegramConfig cfg = resolveConfig();
        if (cfg.token().isBlank() || cfg.chatId().isBlank()) {
            return Map.of("ok", false, "error", "Debe configurar el Bot Token (de @BotFather) y el Chat ID primero.");
        }
        postgresRepository.recordTelegramActivity("Panel Admin", "PRUEBA_CONEXION",
            "Se envió el menú interactivo y prueba de conexión al bot de Telegram.");
        return sendMessageWithMenu(cfg.token(), cfg.chatId(),
            "✅ <b>Conexión Exitosa — Plataforma Electoral AWS</b>\n"
                + "El bot quedó activo en modo interactivo. Usa los botones para probar informes, simulaciones y control electoral en vivo:");
    }

    public Map<String, Object> sendRunoffReport(String customChatId) {
        TelegramConfig cfg = resolveConfig();
        String chat = (customChatId != null && !customChatId.isBlank()) ? customChatId : cfg.chatId();
        if (cfg.token().isBlank() || chat.isBlank()) {
            return Map.of("ok", false, "error", "Bot Token o Chat ID no configurado.");
        }
        postgresRepository.recordTelegramActivity("Panel Admin", "ENVIO_2DA_VUELTA",
            "Se envió el Informe de Predicción de Segunda Vuelta a Telegram.");
        return sendMessageWithMenu(cfg.token(), chat, buildRunoffReportText());
    }

    public Map<String, Object> sendNodesReport(String customChatId) {
        TelegramConfig cfg = resolveConfig();
        String chat = (customChatId != null && !customChatId.isBlank()) ? customChatId : cfg.chatId();
        if (cfg.token().isBlank() || chat.isBlank()) {
            return Map.of("ok", false, "error", "Bot Token o Chat ID no configurado.");
        }
        postgresRepository.recordTelegramActivity("Panel Admin", "ENVIO_NODOS_AWS",
            "Se envió el Informe de Distribución de Carga de Nodos AWS a Telegram.");
        return sendMessageWithMenu(cfg.token(), chat, buildNodesReportText());
    }

    public Map<String, Object> sendElectoralSummary(String customChatId) {
        TelegramConfig cfg = resolveConfig();
        String chat = (customChatId != null && !customChatId.isBlank()) ? customChatId : cfg.chatId();
        if (cfg.token().isBlank() || chat.isBlank()) {
            return Map.of("ok", false, "error", "Bot Token o Chat ID no configurado.");
        }
        postgresRepository.recordTelegramActivity("Panel Admin", "ENVIO_RESUMEN",
            "Se envió el Resumen Electoral Completo a Telegram.");
        return sendMessageWithMenu(cfg.token(), chat, buildLiveSummaryText());
    }

    public String buildRunoffReportText() {
        broadcastService.forceRefreshNow();
        GlobalResultRecord g = broadcastService.getLatestGlobal();
        RunoffPredictionRecord nac = RunoffPredictor.predict(g, ElectionType.NACIONAL, "ALL");
        RunoffPredictionRecord reg = RunoffPredictor.predict(g, ElectionType.REGIONAL, "ALL");

        StringBuilder sb = new StringBuilder();
        sb.append("🏆 <b>INFORME DE PREDICCIÓN — SEGUNDA VUELTA</b>\n\n");
        sb.append("🇨🇴 <b>ELECCIÓN NACIONAL:</b>\n").append(escape(nac.verdictTitle())).append("\n");
        for (int i = 0; i < nac.finalists().size(); i++) {
            var f = nac.finalists().get(i);
            sb.append(String.format("  %d) <b>%s</b>: %.2f%% (%,d votos · %d reales | Prob. 2da vuelta: %.1f%%)\n",
                i + 1, escape(f.name()), f.currentSharePct(), f.votes(), f.realVotes(), f.winProbabilityPct()));
        }
        sb.append(String.format("  <i>Margen de error (95%%): ±%.2f%%</i>\n\n", nac.marginOfErrorPct()));

        sb.append("🗺️ <b>ELECCIÓN REGIONAL:</b>\n").append(escape(reg.verdictTitle())).append("\n");
        for (int i = 0; i < reg.finalists().size(); i++) {
            var f = reg.finalists().get(i);
            sb.append(String.format("  %d) <b>%s</b>: %.2f%% (%,d votos · %d reales | Prob. 2da vuelta: %.1f%%)\n",
                i + 1, escape(f.name()), f.currentSharePct(), f.votes(), f.realVotes(), f.winProbabilityPct()));
        }
        return sb.toString();
    }

    public String buildNodesReportText() {
        List<Map<String, Object>> nodes = postgresRepository.queryNodeTelemetry();
        long totalLoad = nodes.stream()
            .mapToLong(n -> ((Number) n.getOrDefault("requests_handled", 0)).longValue()
                + ((Number) n.getOrDefault("votes_processed", 0)).longValue())
            .sum();
        if (totalLoad == 0) totalLoad = 1;

        StringBuilder sb = new StringBuilder();
        sb.append("⚖️ <b>INFORME DISTRIBUCIÓN DE CARGA — AWS ALB</b>\n");
        sb.append("• <b>Balanceador:</b> Application Load Balancer (Multi-AZ us-east-1a / us-east-1b)\n");
        sb.append("• <b>Estado Electoral:</b> <b>").append(escape(postgresRepository.getLifecycleStateName())).append("</b>\n\n");
        for (Map<String, Object> n : nodes) {
            long reqs = ((Number) n.getOrDefault("requests_handled", 0)).longValue();
            long votes = ((Number) n.getOrDefault("votes_processed", 0)).longValue();
            double pct = ((reqs + votes) * 100.0) / totalLoad;
            sb.append(String.format("🖥️ <code>%s</code> (%s)\n   Peticiones: <b>%,d</b> · Votos: <b>%,d</b> · Carga: <b>%.1f%%</b>\n",
                escape(String.valueOf(n.get("node_id"))),
                escape(String.valueOf(n.get("availability_zone"))),
                reqs, votes, pct));
        }
        return sb.toString();
    }

    public String buildLiveSummaryText() {
        broadcastService.forceRefreshNow();
        GlobalResultRecord g = broadcastService.getLatestGlobal();
        Map<String, Object> counters = postgresRepository.queryDatabaseCounters();
        return String.format(
            "📊 <b>RESUMEN ELECTORAL EN VIVO (AWS)</b>\n"
                + "• <b>Estado (Patrón State):</b> %s\n"
                + "• <b>Total Votos:</b> %,d (Nacional: %,d | Regional: %,d)\n"
                + "• <b>Votos REALES (Ciudadanos):</b> %,d\n"
                + "• <b>Votos SIMULADOS (Flujo):</b> %,d\n"
                + "• <b>Alertas Anti-Fraude:</b> %s\n"
                + "• <b>Entidades Bloqueadas:</b> %s",
            escape(postgresRepository.getLifecycleStateName()),
            g.totalVotes(), g.totalNationalVotes(), g.totalRegionalVotes(),
            g.totalRealVotes(), g.totalSimulatedVotes(),
            counters.getOrDefault("total_incidents", 0),
            counters.getOrDefault("total_blocked", 0)
        );
    }

    public Map<String, Object> handleWebhookUpdate(Map<String, Object> update) {
        TelegramConfig cfg = resolveConfig();
        if (cfg.token().isBlank()) return Map.of("ok", false, "error", "Bot Token no configurado");
        JsonNode node = objectMapper.valueToTree(update);
        processUpdateJson(cfg.token(), node);
        return Map.of("ok", true);
    }

    private Map<String, Object> sendMessageWithMenu(String botToken, String chatId, String htmlText) {
        return sendMessage(botToken, chatId, htmlText, true);
    }

    private void answerCallbackQuery(String botToken, String callbackId) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.telegram.org/bot" + botToken + "/answerCallbackQuery"))
                .timeout(Duration.ofSeconds(4))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"callback_query_id\":\"" + callbackId + "\"}"))
                .build();
            httpClient.send(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
        }
    }

    public Map<String, Object> sendMessage(String botToken, String chatId, String htmlText, boolean withKeyboard) {
        try {
            String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("chat_id", chatId);
            payload.put("text", htmlText);
            payload.put("parse_mode", "HTML");
            if (withKeyboard) {
                payload.put("reply_markup", Map.of("inline_keyboard", List.of(
                    List.of(
                        Map.of("text", "🏆 Informe 2da Vuelta", "callback_data", "cmd_runoff"),
                        Map.of("text", "⚖️ Distribución Nodos AWS", "callback_data", "cmd_nodes")
                    ),
                    List.of(
                        Map.of("text", "📊 Resumen En Vivo", "callback_data", "cmd_summary"),
                        Map.of("text", "⚡ Simular +25 Votos", "callback_data", "cmd_burst")
                    ),
                    List.of(
                        Map.of("text", "▶️ Activar Simulador", "callback_data", "cmd_sim_on"),
                        Map.of("text", "⏸️ Pausar Simulador", "callback_data", "cmd_sim_off"),
                        Map.of("text", "🛡️ Ataque Bot", "callback_data", "cmd_bot")
                    ),
                    List.of(
                        Map.of("text", "🟢 HABILITADO", "callback_data", "cmd_state_habilitado"),
                        Map.of("text", "🟡 AUDITORÍA", "callback_data", "cmd_state_auditoria"),
                        Map.of("text", "🔴 CERRAR", "callback_data", "cmd_state_cerrado")
                    )
                )));
            }

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            boolean success = resp.statusCode() >= 200 && resp.statusCode() < 300;
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
