package com.voting.platform.dashboard.global.service;

import com.voting.platform.domain.model.GlobalResultRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service managing real-time global voting dashboards.
 * Holds latest snapshot and pushes updates reactively to web clients via SSE / WebSockets.
 */
@Service
public class GlobalDashboardService {

    private static final Logger log = LoggerFactory.getLogger(GlobalDashboardService.class);

    private final AtomicReference<GlobalResultRecord> latestResult;
    private final List<SseEmitter> activeEmitters;

    public GlobalDashboardService() {
        this.latestResult = new AtomicReference<>(
            new GlobalResultRecord(Collections.emptyMap(), 0L, Instant.now())
        );
        this.activeEmitters = new CopyOnWriteArrayList<>();
    }

    public void updateResults(GlobalResultRecord newResults) {
        Objects.requireNonNull(newResults, "newResults cannot be null");
        this.latestResult.set(newResults);
        broadcastToClients(newResults);
    }

    public GlobalResultRecord getLatestResult() {
        return latestResult.get();
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(180_000L); // 3 minutes timeout
        activeEmitters.add(emitter);

        emitter.onCompletion(() -> activeEmitters.remove(emitter));
        emitter.onTimeout(() -> activeEmitters.remove(emitter));
        emitter.onError(e -> activeEmitters.remove(emitter));

        // Immediately push latest snapshot to newly connected client
        try {
            emitter.send(SseEmitter.event()
                .name("global-results")
                .data(latestResult.get()));
        } catch (IOException e) {
            activeEmitters.remove(emitter);
        }

        return emitter;
    }

    private void broadcastToClients(GlobalResultRecord results) {
        List<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();
        for (SseEmitter emitter : activeEmitters) {
            try {
                emitter.send(SseEmitter.event()
                    .name("global-results")
                    .data(results));
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
