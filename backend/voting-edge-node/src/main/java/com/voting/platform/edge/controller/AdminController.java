package com.voting.platform.edge.controller;

import com.voting.platform.domain.model.CandidateCatalog;
import com.voting.platform.domain.model.City;
import com.voting.platform.domain.model.ElectionType;
import com.voting.platform.domain.model.EligibleVoterRecord;
import com.voting.platform.domain.model.Region;
import com.voting.platform.domain.model.VoteRecord;
import com.voting.platform.domain.model.VoteSource;
import com.voting.platform.domain.model.VoteTicketCode;
import com.voting.platform.domain.model.VoterId;
import com.voting.platform.domain.port.NotificationPort;
import com.voting.platform.edge.infra.InMemoryEligibilityStore;
import com.voting.platform.edge.infra.KafkaEdgeClient;
import com.voting.platform.edge.infra.PostgresEdgeRepository;
import com.voting.platform.edge.service.DashboardBroadcastService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Electoral Administration & Live Telemetry Controller (No login wall required).
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final PostgresEdgeRepository postgresRepository;
    private final DashboardBroadcastService broadcastService;
    private final InMemoryEligibilityStore eligibilityStore;
    private final KafkaEdgeClient kafkaEdgeClient;
    private final NotificationPort notificationPort;
    private final String nodeId;
    private final String availabilityZone;

    public AdminController(
        PostgresEdgeRepository postgresRepository,
        DashboardBroadcastService broadcastService,
        InMemoryEligibilityStore eligibilityStore,
        KafkaEdgeClient kafkaEdgeClient,
        NotificationPort notificationPort,
        @Value("${voting.node-id:voting-edge-1}") String nodeId,
        @Value("${voting.availability-zone:us-east-1a}") String availabilityZone
    ) {
        this.postgresRepository = Objects.requireNonNull(postgresRepository, "postgresRepository cannot be null");
        this.broadcastService = Objects.requireNonNull(broadcastService, "broadcastService cannot be null");
        this.eligibilityStore = Objects.requireNonNull(eligibilityStore, "eligibilityStore cannot be null");
        this.kafkaEdgeClient = Objects.requireNonNull(kafkaEdgeClient, "kafkaEdgeClient cannot be null");
        this.notificationPort = notificationPort;
        this.nodeId = nodeId;
        this.availabilityZone = availabilityZone;
    }

    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> overview() {
        postgresRepository.incrementNodeRequests(false);
        broadcastService.forceRefreshNow();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("servedByNode", nodeId);
        body.put("availabilityZone", availabilityZone);
        body.put("ktableSize", eligibilityStore.size());
        body.put("control", postgresRepository.readControlPanel());
        body.put("globalResults", broadcastService.getLatestGlobal());
        body.put("regionalResults", broadcastService.getLatestRegionalMap());
        body.put("nodes", postgresRepository.queryNodeTelemetry());
        body.put("dbCounters", postgresRepository.queryDatabaseCounters());
        body.put("candidates", CandidateCatalog.all());
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.ok()
            .header("X-Node-Id", nodeId)
            .body(body);
    }

    @GetMapping("/votes")
    public ResponseEntity<Map<String, Object>> auditVotes(
        @RequestParam(defaultValue = "ALL") String electionType,
        @RequestParam(defaultValue = "ALL") String voteSource,
        @RequestParam(defaultValue = "ALL") String region,
        @RequestParam(defaultValue = "") String q,
        @RequestParam(defaultValue = "100") int limit
    ) {
        List<Map<String, Object>> rows = postgresRepository.queryAuditVotes(electionType, voteSource, region, q, limit);
        return ResponseEntity.ok()
            .header("X-Node-Id", nodeId)
            .body(Map.of("servedByNode", nodeId, "count", rows.size(), "votes", rows));
    }

    @GetMapping("/voters")
    public ResponseEntity<Map<String, Object>> registeredVoters() {
        List<Map<String, Object>> rows = postgresRepository.queryRegisteredVoters();
        return ResponseEntity.ok()
            .header("X-Node-Id", nodeId)
            .body(Map.of(
                "servedByNode", nodeId,
                "ktableSize", eligibilityStore.size(),
                "voters", rows
            ));
    }

    @PostMapping("/voters")
    public ResponseEntity<Map<String, Object>> addVoter(@RequestBody Map<String, Object> payload) {
        String cedula = String.valueOf(payload.getOrDefault("voterId", "")).trim();
        String fullName = String.valueOf(payload.getOrDefault("fullName", "Ciudadano Registrado")).trim();
        String city = String.valueOf(payload.getOrDefault("city", "Bogotá")).trim();
        String regionStr = String.valueOf(payload.getOrDefault("region", "")).trim();

        VoterId voterId = VoterId.of(cedula);
        Region region = City.resolveRegionFor(city, regionStr);

        postgresRepository.upsertRegisteredVoter(voterId.value(), fullName, city, region.name(), true);
        EligibleVoterRecord record = new EligibleVoterRecord(voterId, fullName, region, Instant.now());
        eligibilityStore.registerEligible(record);
        kafkaEdgeClient.publishEligibleVoter(record);

        return ResponseEntity.ok()
            .header("X-Node-Id", nodeId)
            .body(Map.of(
                "status", "REGISTERED",
                "voterId", voterId.value(),
                "region", region.name(),
                "servedByNode", nodeId
            ));
    }

    @PatchMapping("/voters/{voterId}")
    public ResponseEntity<Map<String, Object>> toggleVoterEligibility(
        @PathVariable String voterId,
        @RequestBody Map<String, Object> payload
    ) {
        boolean eligible = Boolean.parseBoolean(String.valueOf(payload.getOrDefault("eligible", true)));
        VoterId id = VoterId.of(voterId);
        postgresRepository.setVoterEligibility(id.value(), eligible);

        if (eligible) {
            EligibleVoterRecord record = new EligibleVoterRecord(id, "Ciudadano Habilitado", Region.CENTRO, Instant.now());
            eligibilityStore.registerEligible(record);
            kafkaEdgeClient.publishEligibleVoter(record);
        } else {
            kafkaEdgeClient.publishVoterRevocation(id);
        }

        return ResponseEntity.ok()
            .header("X-Node-Id", nodeId)
            .body(Map.of("voterId", id.value(), "eligible", eligible, "servedByNode", nodeId));
    }

    @GetMapping("/incidents")
    public ResponseEntity<Map<String, Object>> securityIncidents(@RequestParam(defaultValue = "50") int limit) {
        List<Map<String, Object>> incidents = postgresRepository.querySecurityIncidents(limit);
        List<Map<String, Object>> blocked = postgresRepository.queryBlockedEntities();
        return ResponseEntity.ok()
            .header("X-Node-Id", nodeId)
            .body(Map.of(
                "servedByNode", nodeId,
                "count", incidents.size(),
                "incidents", incidents,
                "blockedEntities", blocked
            ));
    }

    @DeleteMapping("/blocked/{entityKey}")
    public ResponseEntity<Map<String, Object>> unblock(@PathVariable String entityKey) {
        postgresRepository.unblockEntity(entityKey);
        return ResponseEntity.ok()
            .header("X-Node-Id", nodeId)
            .body(Map.of("unblocked", entityKey, "servedByNode", nodeId));
    }

    @PostMapping("/lifecycle")
    public ResponseEntity<Map<String, Object>> updateLifecycle(@RequestBody Map<String, Object> payload) {
        if (payload.containsKey("lifecycleState")) {
            String nextState = String.valueOf(payload.get("lifecycleState")).trim().toUpperCase();
            if (!List.of("HABILITADO", "EN_AUDITORIA", "CERRADO").contains(nextState)) {
                throw new IllegalArgumentException("Estado inválido. Use: HABILITADO, EN_AUDITORIA o CERRADO.");
            }
            postgresRepository.updateLifecycleStateName(nextState);
            if (notificationPort != null) {
                notificationPort.notifyLifecycleChange(nextState, nodeId);
            }
        }
        if (payload.containsKey("strictCensusMode")) {
            boolean strict = Boolean.parseBoolean(String.valueOf(payload.get("strictCensusMode")));
            postgresRepository.setStrictCensusMode(strict);
        }
        return ResponseEntity.ok()
            .header("X-Node-Id", nodeId)
            .body(Map.of("servedByNode", nodeId, "control", postgresRepository.readControlPanel()));
    }

    @PostMapping("/simulator")
    public ResponseEntity<Map<String, Object>> updateSimulator(@RequestBody Map<String, Object> payload) {
        Boolean enabled = payload.containsKey("simulatorEnabled")
            ? Boolean.parseBoolean(String.valueOf(payload.get("simulatorEnabled"))) : null;
        Integer rateMs = payload.containsKey("simulatorRateMs")
            ? Integer.parseInt(String.valueOf(payload.get("simulatorRateMs"))) : null;
        Integer batchSize = payload.containsKey("simulatorBatchSize")
            ? Integer.parseInt(String.valueOf(payload.get("simulatorBatchSize"))) : null;
        Boolean botAttack = payload.containsKey("triggerBotAttack")
            ? Boolean.parseBoolean(String.valueOf(payload.get("triggerBotAttack"))) : null;

        postgresRepository.updateSimulatorSettings(enabled, rateMs, batchSize, botAttack);
        return ResponseEntity.ok()
            .header("X-Node-Id", nodeId)
            .body(Map.of("servedByNode", nodeId, "control", postgresRepository.readControlPanel()));
    }

    @PostMapping("/simulator/burst")
    public ResponseEntity<Map<String, Object>> triggerBurst(@RequestParam(defaultValue = "25") int count) {
        int clamped = Math.max(5, Math.min(count, 100));
        List<String> cities = City.allCityNames();
        List<String> nacPool = CandidateCatalog.byElectionType(ElectionType.NACIONAL).stream()
            .map(CandidateCatalog.CandidateProfile::id).toList();
        List<String> regPool = CandidateCatalog.byElectionType(ElectionType.REGIONAL).stream()
            .map(CandidateCatalog.CandidateProfile::id).toList();

        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < clamped; i++) {
            ElectionType type = (i % 2 == 0) ? ElectionType.NACIONAL : ElectionType.REGIONAL;
            List<String> pool = type == ElectionType.NACIONAL ? nacPool : regPool;
            String candidateId = pool.get(rnd.nextInt(pool.size()));
            String city = cities.get(rnd.nextInt(cities.size()));
            String simId = "BURST-" + System.currentTimeMillis() + "-" + i;
            String ip = "172.16." + rnd.nextInt(1, 200) + "." + rnd.nextInt(1, 250);

            VoteRecord vote = VoteRecord.create(
                simId,
                candidateId,
                city,
                null,
                type.name(),
                VoteSource.SIMULATED.name(),
                ip,
                VoteTicketCode.generate(type).value(),
                nodeId,
                Instant.now()
            );
            kafkaEdgeClient.publishRawVote(vote);
            postgresRepository.incrementNodeRequests(true);
        }

        return ResponseEntity.ok()
            .header("X-Node-Id", nodeId)
            .body(Map.of(
                "status", "BURST_DISPATCHED",
                "count", clamped,
                "servedByNode", nodeId
            ));
    }
}
