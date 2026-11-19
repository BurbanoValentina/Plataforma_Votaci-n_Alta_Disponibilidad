package com.voting.platform.edge.controller;

import com.voting.platform.domain.model.City;
import com.voting.platform.domain.model.Region;
import com.voting.platform.edge.repository.PostgresEdgeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Live Geolocation Activity API for the Interactive Colombia Electoral Map.
 * Returns exact coordinates + REAL vs SIMULATED vote counts for all 21 Colombian cities.
 */
@RestController
@RequestMapping("/api/map")
public class MapController {

    private static final Map<String, double[]> CITY_COORDS = Map.ofEntries(
        Map.entry("Bogotá", new double[]{4.7110, -74.0721}),
        Map.entry("Medellín", new double[]{6.2442, -75.5812}),
        Map.entry("Bucaramanga", new double[]{7.1193, -73.1227}),
        Map.entry("Manizales", new double[]{5.0703, -75.5138}),
        Map.entry("Tunja", new double[]{5.5353, -73.3678}),
        Map.entry("Barranquilla", new double[]{10.9685, -74.7813}),
        Map.entry("Cartagena", new double[]{10.3910, -75.4794}),
        Map.entry("Santa Marta", new double[]{11.2408, -74.1990}),
        Map.entry("Montería", new double[]{8.7479, -75.8814}),
        Map.entry("Pasto", new double[]{1.2136, -77.2811}),
        Map.entry("Popayán", new double[]{2.4448, -76.6147}),
        Map.entry("Neiva", new double[]{2.9273, -75.2819}),
        Map.entry("Florencia", new double[]{1.6144, -75.6062}),
        Map.entry("Cúcuta", new double[]{7.8891, -72.4967}),
        Map.entry("Villavicencio", new double[]{4.1420, -73.6266}),
        Map.entry("Yopal", new double[]{5.3378, -72.3959}),
        Map.entry("Arauca", new double[]{7.0847, -70.7591}),
        Map.entry("Cali", new double[]{3.4516, -76.5320}),
        Map.entry("Pereira", new double[]{4.8087, -75.6906}),
        Map.entry("Armenia", new double[]{4.5339, -75.6811}),
        Map.entry("Quibdó", new double[]{5.6947, -76.6611})
    );

    private final PostgresEdgeRepository postgresRepository;
    private final String nodeId;

    public MapController(
        PostgresEdgeRepository postgresRepository,
        @Value("${voting.node-id:voting-edge-1}") String nodeId
    ) {
        this.postgresRepository = Objects.requireNonNull(postgresRepository, "postgresRepository cannot be null");
        this.nodeId = nodeId;
    }

    @GetMapping("/activity")
    public ResponseEntity<Map<String, Object>> mapActivity(
        @RequestParam(defaultValue = "ALL") String electionType
    ) {
        List<Map<String, Object>> dbRows = postgresRepository.queryCityMapActivity(electionType);
        Map<String, Map<String, Object>> byCity = new HashMap<>();
        for (Map<String, Object> r : dbRows) {
            byCity.put(String.valueOf(r.get("city")).toLowerCase(), r);
        }

        List<Map<String, Object>> points = new ArrayList<>();
        for (Map.Entry<String, Region> entry : City.catalog().entrySet()) {
            String cityName = entry.getKey();
            Region reg = entry.getValue();
            double[] coords = CITY_COORDS.getOrDefault(cityName, new double[]{4.7110, -74.0721});
            Map<String, Object> stats = byCity.get(cityName.toLowerCase());

            long total = stats != null ? ((Number) stats.getOrDefault("total_votes", 0)).longValue() : 0L;
            long real = stats != null ? ((Number) stats.getOrDefault("real_votes", 0)).longValue() : 0L;
            long sim = stats != null ? ((Number) stats.getOrDefault("simulated_votes", 0)).longValue() : 0L;

            Map<String, Object> pt = new LinkedHashMap<>();
            pt.put("city", cityName);
            pt.put("region", reg.name());
            pt.put("lat", coords[0]);
            pt.put("lng", coords[1]);
            pt.put("totalVotes", total);
            pt.put("realVotes", real);
            pt.put("simulatedVotes", sim);
            pt.put("hasRealVotes", real > 0);
            pt.put("lastRealAt", stats != null && stats.get("last_real_at") != null ? stats.get("last_real_at").toString() : null);
            points.add(pt);
        }

        return ResponseEntity.ok()
            .header("X-Node-Id", nodeId)
            .body(Map.of("servedByNode", nodeId, "electionType", electionType.toUpperCase(), "points", points));
    }
}
