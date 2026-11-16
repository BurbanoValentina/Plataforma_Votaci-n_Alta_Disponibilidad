package com.voting.platform.dashboard.regional.service;

import com.voting.platform.domain.model.Region;
import com.voting.platform.domain.model.RegionalResultRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service managing regional voting results received from RabbitMQ Topic Exchange (results.*).
 * Distributes updates reactively to connected web clients.
 */
@Service
public class RegionalDashboardService {

    private static final Logger log = LoggerFactory.getLogger(RegionalDashboardService.class);

    private final Map<Region, RegionalResultRecord> regionalResults;
    private final List<SseEmitter> activeEmitters;

    public RegionalDashboardService() {
        this.regionalResults = new ConcurrentHashMap<>();
        for (Region r : Region.values()) {
            this.regionalResults.put(r, new RegionalResultRecord(
                r, Collections.emptyMap(), Collections.emptyMap(), 0L, Instant.now()
            ));
        }
        this.activeEmitters = new CopyOnWriteArrayList<>();
    }

    public void updateRegionalResult(RegionalResultRecord record) {
        Objects.requireNonNull(record, "record cannot be null");
        regionalResults.put(record.region(), record);
        broadcastToClients(record);
        log.debug("Updated regional dashboard for: {}", record.region());
    }

    public Map<Region, RegionalResultRecord> getAllRegionalResults() {
        return Collections.unmodifiableMap(regionalResults);
    }

    public RegionalResultRecord getRegionResult(Region region) {
        return regionalResults.get(region);
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(180_000L);
        activeEmitters.add(emitter);

        emitter.onCompletion(() -> activeEmitters.remove(emitter));
        emitter.onTimeout(() -> activeEmitters.remove(emitter));
        emitter.onError(e -> activeEmitters.remove(emitter));

        // Push initial full state
        try {
            emitter.send(SseEmitter.event()
                .name("regional-init")
                .data(getAllRegionalResults()));
        } catch (IOException e) {
            activeEmitters.remove(emitter);
        }

        return emitter;
    }

    private void broadcastToClients(RegionalResultRecord record) {
        List<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();
        for (SseEmitter emitter : activeEmitters) {
            try {
                emitter.send(SseEmitter.event()
                    .name("regional-update")
                    .data(record));
            } catch (Exception e) {
                deadEmitters.add(emitter);
            }
        }
        activeEmitters.removeAll(deadEmitters);
    }

    public int getSubscriberCount() {
        return activeEmitters.size();
    }
}
