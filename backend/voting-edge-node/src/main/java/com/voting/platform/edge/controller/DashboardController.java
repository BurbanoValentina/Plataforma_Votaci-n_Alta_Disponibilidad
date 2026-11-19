package com.voting.platform.edge.controller;

import com.voting.platform.domain.dto.response.GlobalResultRecord;
import com.voting.platform.domain.dto.response.RegionalResultRecord;
import com.voting.platform.edge.service.DashboardBroadcastService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.Objects;

/**
 * Real-Time Dashboard REST & Server-Sent Events (SSE) Controller.
 * Fed directly by RabbitMQ Fanout (live_results_global) and Topic (live_results_regional).
 */
@RestController
public class DashboardController {

    private final DashboardBroadcastService broadcastService;
    private final String nodeId;

    public DashboardController(
        DashboardBroadcastService broadcastService,
        @Value("${voting.node-id:voting-edge-1}") String nodeId
    ) {
        this.broadcastService = Objects.requireNonNull(broadcastService, "broadcastService cannot be null");
        this.nodeId = nodeId;
    }

    @GetMapping("/api/results/global")
    public ResponseEntity<GlobalResultRecord> getGlobalResults() {
        return ResponseEntity.ok()
            .header("X-Node-Id", nodeId)
            .body(broadcastService.getLatestGlobal());
    }

    @GetMapping(value = {"/api/results/global/stream", "/events/global"}, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamGlobalResults() {
        return broadcastService.registerGlobalEmitter();
    }

    @GetMapping("/api/results/regional")
    public ResponseEntity<Map<String, RegionalResultRecord>> getRegionalResults() {
        return ResponseEntity.ok()
            .header("X-Node-Id", nodeId)
            .body(broadcastService.getLatestRegionalMap());
    }

    @GetMapping(value = {"/api/results/regional/stream", "/events/regional"}, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamRegionalResults() {
        return broadcastService.registerRegionalEmitter();
    }
}
