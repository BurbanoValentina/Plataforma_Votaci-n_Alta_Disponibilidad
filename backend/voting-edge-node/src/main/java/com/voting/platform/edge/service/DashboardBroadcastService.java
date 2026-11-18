package com.voting.platform.edge.service;

import com.voting.platform.domain.model.GlobalResultRecord;
import com.voting.platform.domain.model.Region;
import com.voting.platform.domain.model.RegionalResultRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages live Global & Regional result caches and broadcasts real-time updates
 * via Server-Sent Events (SSE) to connected web browsers.
 */
@Service
public class DashboardBroadcastService {

    private final AtomicReference<GlobalResultRecord> latestGlobal = new AtomicReference<>(GlobalResultRecord.empty());
    private final Map<Region, RegionalResultRecord> latestRegional = Collections.synchronizedMap(new EnumMap<>(Region.class));

    private final CopyOnWriteArrayList<SseEmitter> globalEmitters = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<SseEmitter> regionalEmitters = new CopyOnWriteArrayList<>();

    public DashboardBroadcastService() {
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
        latestGlobal.set(record);
        for (SseEmitter emitter : globalEmitters) {
            try {
                emitter.send(SseEmitter.event().name("global-results").data(record));
            } catch (IOException | IllegalStateException e) {
                globalEmitters.remove(emitter);
            }
        }
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

    public GlobalResultRecord getLatestGlobal() {
        return latestGlobal.get();
    }

    public Map<String, RegionalResultRecord> getLatestRegionalMap() {
        Map<String, RegionalResultRecord> out = new java.util.LinkedHashMap<>();
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
            emitter.send(SseEmitter.event().name("global-results").data(latestGlobal.get()));
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
