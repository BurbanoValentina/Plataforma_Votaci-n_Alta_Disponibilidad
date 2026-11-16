package com.voting.platform.dashboard.global.controller;

import com.voting.platform.dashboard.global.service.GlobalDashboardService;
import com.voting.platform.domain.model.GlobalResultRecord;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Objects;

/**
 * Controller serving the Global Results API and real-time event stream.
 */
@RestController
@RequestMapping("/api/results/global")
public class GlobalDashboardController {

    private final GlobalDashboardService dashboardService;

    public GlobalDashboardController(GlobalDashboardService dashboardService) {
        this.dashboardService = Objects.requireNonNull(dashboardService, "dashboardService cannot be null");
    }

    @GetMapping
    public ResponseEntity<GlobalResultRecord> getGlobalResults() {
        return ResponseEntity.ok(dashboardService.getLatestResult());
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamGlobalResults() {
        return dashboardService.subscribe();
    }

    @PostMapping("/update")
    public ResponseEntity<Void> receiveUpdateFromBroker(@RequestBody GlobalResultRecord update) {
        dashboardService.updateResults(update);
        return ResponseEntity.ok().build();
    }
}
