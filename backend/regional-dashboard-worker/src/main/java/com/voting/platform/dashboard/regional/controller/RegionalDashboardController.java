package com.voting.platform.dashboard.regional.controller;

import com.voting.platform.dashboard.regional.service.RegionalDashboardService;
import com.voting.platform.domain.model.Region;
import com.voting.platform.domain.model.RegionalResultRecord;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.Objects;

/**
 * Controller serving Regional Results API and real-time event streams.
 */
@RestController
@RequestMapping("/api/results/regional")
public class RegionalDashboardController {

    private final RegionalDashboardService dashboardService;

    public RegionalDashboardController(RegionalDashboardService dashboardService) {
        this.dashboardService = Objects.requireNonNull(dashboardService, "dashboardService cannot be null");
    }

    @GetMapping
    public ResponseEntity<Map<Region, RegionalResultRecord>> getAllRegionalResults() {
        return ResponseEntity.ok(dashboardService.getAllRegionalResults());
    }

    @GetMapping("/{region}")
    public ResponseEntity<RegionalResultRecord> getSingleRegion(@PathVariable String region) {
        Region r = Region.fromString(region);
        RegionalResultRecord record = dashboardService.getRegionResult(r);
        return ResponseEntity.ok(record);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamRegionalResults() {
        return dashboardService.subscribe();
    }

    @PostMapping("/update")
    public ResponseEntity<Void> receiveUpdateFromBroker(@RequestBody RegionalResultRecord record) {
        dashboardService.updateRegionalResult(record);
        return ResponseEntity.ok().build();
    }
}
