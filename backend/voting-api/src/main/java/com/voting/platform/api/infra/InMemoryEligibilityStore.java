package com.voting.platform.api.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voting.platform.domain.model.EligibleVoterRecord;
import com.voting.platform.domain.model.Region;
import com.voting.platform.domain.model.VoterId;
import com.voting.platform.domain.port.VoterEligibilityStore;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory materialized KTable representing the latest snapshot
 * of eligible voters synced from the Kafka eligible_voters compacted topic.
 */
@Component
public class InMemoryEligibilityStore implements VoterEligibilityStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryEligibilityStore.class);

    private final ConcurrentHashMap<VoterId, EligibleVoterRecord> eligibleMap;
    private final ObjectMapper objectMapper;

    public InMemoryEligibilityStore(ObjectMapper objectMapper) {
        this.eligibleMap = new ConcurrentHashMap<>();
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
    }

    public InMemoryEligibilityStore() {
        this(new ObjectMapper());
    }

    @PostConstruct
    public void init() {
        try (InputStream is = getClass().getResourceAsStream("/voters.json")) {
            if (is != null) {
                JsonNode array = objectMapper.readTree(is);
                if (array.isArray()) {
                    for (JsonNode node : array) {
                        String idStr = node.path("voterId").asText();
                        String name = node.path("fullName").asText();
                        String regStr = node.path("region").asText("CENTRO");
                        if (!idStr.isBlank()) {
                            VoterId voterId = VoterId.of(idStr);
                            Region region = Region.valueOf(regStr.toUpperCase());
                            EligibleVoterRecord record = new EligibleVoterRecord(voterId, name, region, Instant.now());
                            registerEligible(record);
                        }
                    }
                    log.info("Materialized {} eligible voters into local KTable store from voters.json", eligibleMap.size());
                }
            } else {
                log.warn("voters.json not found in classpath for initial KTable snapshot.");
            }
        } catch (Exception e) {
            log.error("Failed to load initial voters into KTable: {}", e.getMessage(), e);
        }
    }

    @Override
    public boolean isEligible(VoterId voterId) {
        if (voterId == null) return false;
        return eligibleMap.containsKey(voterId);
    }

    @Override
    public void registerEligible(EligibleVoterRecord record) {
        Objects.requireNonNull(record, "record cannot be null");
        eligibleMap.put(record.voterId(), record);
        log.debug("KTable synced eligible voter: {}", record.voterId());
    }

    @Override
    public void removeEligible(VoterId voterId) {
        if (voterId != null) {
            eligibleMap.remove(voterId);
            log.debug("KTable removed voter: {}", voterId);
        }
    }

    @Override
    public int size() {
        return eligibleMap.size();
    }

    @Override
    public void clear() {
        eligibleMap.clear();
    }
}
