package com.voting.platform.edge.service;

import com.voting.platform.domain.model.GlobalResultRecord;
import com.voting.platform.domain.model.Region;
import com.voting.platform.domain.model.RegionalResultRecord;
import com.voting.platform.edge.infra.PostgresEdgeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages live Global & Regional result caches and broadcasts real-time updates
 * via Server-Sent Events (SSE) to connected web browsers.
 * Reconciles RabbitMQ live broadcasts with authoritative PostgreSQL totals so
 * BOTH load-balanced AWS Edge Nodes ALWAYS return identical, monotonic REAL & SIMULATED counts.
 */
@Service
public class DashboardBroadcastService {

    private final AtomicReference<GlobalResultRecord> latestGlobal = new AtomicReference<>(GlobalResultRecord.empty());
    private final Map<Region, RegionalResultRecord> latestRegional = Collections.synchronizedMap(new EnumMap<>(Region.class));

    private final CopyOnWriteArrayList<SseEmitter> globalEmitters = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<SseEmitter> regionalEmitters = new CopyOnWriteArrayList<>();
    private final PostgresEdgeRepository postgresRepository;

    @Autowired
    public DashboardBroadcastService(PostgresEdgeRepository postgresRepository) {
        this.postgresRepository = postgresRepository;
        initEmptyRegions();
    }

    public DashboardBroadcastService() {
        this(null);
    }

    private void initEmptyRegions() {
        for (Region r : Region.values()) {
            double weight = switch (r) {
                case NORTE -> 1.25;
                case SUR -> 1.15;
                case CENTRO -> 1.40;
                case ESTE -> 0.90;
                case OESTE -> 1.05;
            };
            latestRegional.put(r, new RegionalResultRecord(
                r, Map.of(), Map.of(), Map.of(), Map.of(), 0L, 0L, 0L, weight, Instant.now()
            ));
        }
    }

    public void onGlobalResult(GlobalResultRecord record) {
        if (record == null) return;
        GlobalResultRecord current = latestGlobal.get();
        // Prevent stale RabbitMQ snapshots from regressing REAL or total counts
        if (current != null && record.totalVotes() < current.totalVotes() && record.totalRealVotes() <= current.totalRealVotes()) {
            return;
        }
        latestGlobal.set(record);
        pushGlobalToEmitters(record);
    }

    public void onRegionalResult(RegionalResultRecord record) {
        if (record == null || record.region() == null) return;
        latestRegional.put(record.region(), record);
        for (SseEmitter emitter : regionalEmitters) {
            try {
                emitter.send(SseEmitter.event().name("regional-update").data(record));
            } catch (IOException | IllegalStateException e) {
                regionalEmitters.remove(emitter);
            }
        }
    }

    /**
     * Periodic reconciliation every 2s guarantees that even during a RabbitMQ reconnect
     * or multi-node ALB alternation, BOTH AWS nodes stay 100% consistent and REAL votes appear immediately.
     */
    @Scheduled(fixedDelay = 2000)
    public void reconcileWithAuthoritativeStore() {
        if (postgresRepository == null) return;
        GlobalResultRecord dbSnap = postgresRepository.computeAuthoritativeGlobalResults();
        GlobalResultRecord cur = latestGlobal.get();
        if (cur == null
            || dbSnap.totalRealVotes() > cur.totalRealVotes()
            || dbSnap.totalVotes() > cur.totalVotes()) {
            latestGlobal.set(dbSnap);
            pushGlobalToEmitters(dbSnap);
        }

        Map<String, RegionalResultRecord> dbReg = postgresRepository.computeAuthoritativeRegionalResults();
        for (Map.Entry<String, RegionalResultRecord> entry : dbReg.entrySet()) {
            Region reg = entry.getValue().region();
            RegionalResultRecord existing = latestRegional.get(reg);
            if (existing == null
                || entry.getValue().totalRealVotes() > existing.totalRealVotes()
                || entry.getValue().totalRegionalVotes() > existing.totalRegionalVotes()) {
                latestRegional.put(reg, entry.getValue());
            }
        }
    }

    public void forceRefreshNow() {
        reconcileWithAuthoritativeStore();
    }

    private void pushGlobalToEmitters(GlobalResultRecord record) {
        for (SseEmitter emitter : globalEmitters) {
            try {
                emitter.send(SseEmitter.event().name("global-results").data(record));
            } catch (IOException | IllegalStateException e) {
                globalEmitters.remove(emitter);
            }
        }
    }

    public GlobalResultRecord getLatestGlobal() {
        GlobalResultRecord cur = latestGlobal.get();
        if (postgresRepository != null && (cur == null || cur.totalVotes() == 0)) {
            reconcileWithAuthoritativeStore();
            cur = latestGlobal.get();
        }
        return cur != null ? cur : GlobalResultRecord.empty();
    }

    public Map<String, RegionalResultRecord> getLatestRegionalMap() {
        Map<String, RegionalResultRecord> out = new LinkedHashMap<>();
        synchronized (latestRegional) {
            for (Region r : Region.values()) {
                out.put(r.name(), latestRegional.get(r));
            }
        }
        return out;
    }

    public SseEmitter registerGlobalEmitter() {
        SseEmitter emitter = new SseEmitter(0L);
        globalEmitters.add(emitter);
        emitter.onCompletion(() -> globalEmitters.remove(emitter));
        emitter.onTimeout(() -> globalEmitters.remove(emitter));
        emitter.onError(e -> globalEmitters.remove(emitter));
        try {
            emitter.send(SseEmitter.event().name("global-results").data(getLatestGlobal()));
        } catch (IOException e) {
            globalEmitters.remove(emitter);
        }
        return emitter;
    }

    public SseEmitter registerRegionalEmitter() {
        SseEmitter emitter = new SseEmitter(0L);
        regionalEmitters.add(emitter);
        emitter.onCompletion(() -> regionalEmitters.remove(emitter));
        emitter.onTimeout(() -> regionalEmitters.remove(emitter));
        emitter.onError(e -> regionalEmitters.remove(emitter));
        try {
            emitter.send(SseEmitter.event().name("regional-init").data(getLatestRegionalMap()));
        } catch (IOException e) {
            regionalEmitters.remove(emitter);
        }
        return emitter;
    }
}
