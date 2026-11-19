package com.voting.platform.edge.controller;

import com.voting.platform.domain.model.CandidateCatalog;
import com.voting.platform.domain.model.City;
import com.voting.platform.domain.model.ElectionType;
import com.voting.platform.domain.model.Region;
import com.voting.platform.domain.model.VoteRequestRecord;
import com.voting.platform.domain.model.VoteTicketRecord;
import com.voting.platform.edge.infra.InMemoryEligibilityStore;
import com.voting.platform.edge.repository.PostgresEdgeRepository;
import com.voting.platform.edge.service.VotingGatewayService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Public Citizen Voting Gateway Controller.
 * Serves electoral catalog, handles verified voting with duplicate cedula ticket enforcement,
 * ticket lookup, and AWS ALB health checks.
 */
@RestController
public class VotingController {

    private final VotingGatewayService votingService;
    private final PostgresEdgeRepository postgresRepository;
    private final InMemoryEligibilityStore eligibilityStore;
    private final String nodeId;
    private final String availabilityZone;

    public VotingController(
        VotingGatewayService votingService,
        PostgresEdgeRepository postgresRepository,
        InMemoryEligibilityStore eligibilityStore,
        @Value("${voting.node-id:voting-edge-1}") String nodeId,
        @Value("${voting.availability-zone:us-east-1a}") String availabilityZone
    ) {
        this.votingService = Objects.requireNonNull(votingService, "votingService cannot be null");
        this.postgresRepository = Objects.requireNonNull(postgresRepository, "postgresRepository cannot be null");
        this.eligibilityStore = Objects.requireNonNull(eligibilityStore, "eligibilityStore cannot be null");
        this.nodeId = nodeId;
        this.availabilityZone = availabilityZone;
    }

    @GetMapping("/api/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("nodeId", nodeId);
        body.put("availabilityZone", availabilityZone);
        body.put("ktableEligibleCount", eligibilityStore.size());
        body.put("lifecycleState", postgresRepository.getLifecycleStateName());
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.ok()
            .header("X-Node-Id", nodeId)
            .header("X-Availability-Zone", availabilityZone)
            .body(body);
    }

    @GetMapping("/api/catalog")
    public ResponseEntity<Map<String, Object>> catalog() {
        postgresRepository.incrementNodeRequests(false);

        List<Map<String, String>> cities = City.catalog().entrySet().stream()
            .map(e -> Map.of("city", e.getKey(), "region", e.getValue().name()))
            .toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nodeId", nodeId);
        body.put("availabilityZone", availabilityZone);
        body.put("lifecycleState", postgresRepository.getLifecycleStateName());
        body.put("strictCensusMode", postgresRepository.isStrictCensusMode());
        body.put("nationalCandidates", CandidateCatalog.byElectionType(ElectionType.NACIONAL));
        body.put("regionalCandidates", CandidateCatalog.byElectionType(ElectionType.REGIONAL));
        body.put("cities", cities);
        body.put("regions", List.of(
            Map.of("code", Region.NORTE.name(), "multiplier", 1.25),
            Map.of("code", Region.SUR.name(), "multiplier", 1.15),
            Map.of("code", Region.CENTRO.name(), "multiplier", 1.40),
            Map.of("code", Region.ESTE.name(), "multiplier", 0.90),
            Map.of("code", Region.OESTE.name(), "multiplier", 1.05)
        ));

        return ResponseEntity.ok()
            .header("X-Node-Id", nodeId)
            .body(body);
    }

    @PostMapping({"/api/vote", "/vote"})
    public ResponseEntity<Map<String, Object>> submitVote(
        @Valid @RequestBody VoteRequestRecord request,
        HttpServletRequest httpRequest
    ) {
        String clientIp = extractClientIp(httpRequest);
        postgresRepository.incrementNodeRequests(true);
        VoteTicketRecord ticket = votingService.castVote(request, clientIp);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ACCEPTED");
        body.put("message", "Voto verificado y registrado exitosamente. Guarde su comprobante electoral.");
        body.put("nodeId", nodeId);
        body.put("ticket", ticket);

        return ResponseEntity.ok()
            .header("X-Node-Id", nodeId)
            .body(body);
    }

    @GetMapping("/api/tickets/{cedula}")
    public ResponseEntity<Map<String, Object>> lookupTickets(@PathVariable String cedula) {
        postgresRepository.incrementNodeRequests(false);
        List<VoteTicketRecord> tickets = votingService.lookupTicketsByCedula(cedula);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cedula", cedula);
        body.put("nodeId", nodeId);
        body.put("found", !tickets.isEmpty());
        body.put("tickets", tickets);

        return ResponseEntity.ok()
            .header("X-Node-Id", nodeId)
            .body(body);
    }

    private String extractClientIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = req.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        String remote = req.getRemoteAddr();
        return (remote != null && !remote.isBlank()) ? remote : "127.0.0.1";
    }
}
