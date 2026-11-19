package com.voting.platform.edge.controller;

import com.voting.platform.domain.service.RunoffPredictor;
import com.voting.platform.domain.model.ElectionType;
import com.voting.platform.domain.dto.response.GlobalResultRecord;
import com.voting.platform.domain.dto.response.RunoffPredictionRecord;
import com.voting.platform.edge.service.DashboardBroadcastService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Second-Round (Segunda Vuelta) Electoral Prediction API.
 * Computes live Top-2 finalist projections for National & Regional elections
 * with support for ALL, REAL-only, and SIMULATED-only analysis.
 */
@RestController
@RequestMapping("/api/prediction")
public class RunoffPredictionController {

    private final DashboardBroadcastService broadcastService;
    private final String nodeId;

    public RunoffPredictionController(
        DashboardBroadcastService broadcastService,
        @Value("${voting.node-id:voting-edge-1}") String nodeId
    ) {
        this.broadcastService = Objects.requireNonNull(broadcastService, "broadcastService cannot be null");
        this.nodeId = nodeId;
    }

    @GetMapping("/runoff")
    public ResponseEntity<Map<String, Object>> getRunoffPrediction(
        @RequestParam(defaultValue = "ALL") String mode
    ) {
        GlobalResultRecord global = broadcastService.getLatestGlobal();
        RunoffPredictionRecord national = RunoffPredictor.predict(global, ElectionType.NACIONAL, mode);
        RunoffPredictionRecord regional = RunoffPredictor.predict(global, ElectionType.REGIONAL, mode);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("servedByNode", nodeId);
        body.put("mode", mode.toUpperCase());
        body.put("national", national);
        body.put("regional", regional);
        body.put("regionalBreakdown", broadcastService.getLatestRegionalMap());
        body.put("generatedAt", Instant.now().toString());

        return ResponseEntity.ok()
            .header("X-Node-Id", nodeId)
            .body(body);
    }
}
