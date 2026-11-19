package com.voting.platform.edge.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voting.platform.domain.model.EligibleVoterRecord;
import com.voting.platform.domain.model.Region;
import com.voting.platform.domain.model.VoterId;
import com.voting.platform.domain.repository.VoterEligibilityStore;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory materialized KTable representing eligible & revoked voters
 * synced from Kafka compacted topic eligible_voters across all load-balanced edge nodes.
 */
@Component
public class InMemoryEligibilityStore implements VoterEligibilityStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryEligibilityStore.class);

    private final ConcurrentHashMap<VoterId, EligibleVoterRecord> eligibleMap = new ConcurrentHashMap<>();
    private final Set<VoterId> revokedVoters = ConcurrentHashMap.newKeySet();
    private final ObjectMapper objectMapper;

    public InMemoryEligibilityStore() {
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    @PostConstruct
    public void init() {
        revokedVoters.add(VoterId.of("0000000000"));
        try (InputStream is = getClass().getResourceAsStream("/voters.json")) {
            if (is != null) {
                JsonNode array = objectMapper.readTree(is);
                if (array.isArray()) {
                    for (JsonNode node : array) {
                        String idStr = node.path("voterId").asText();
                        String name = node.path("fullName").asText("Ciudadano");
                        String regStr = node.path("region").asText("CENTRO");
                        if (!idStr.isBlank()) {
                            registerEligible(new EligibleVoterRecord(
                                VoterId.of(idStr),
                                name,
                                Region.fromString(regStr),
                                Instant.now()
                            ));
                        }
                    }
                    log.info("Bootstrapped KTable with {} eligible voters from voters.json", eligibleMap.size());
                }
            }
        } catch (Exception e) {
            log.warn("Could not bootstrap voters.json: {}", e.getMessage());
        }
    }

    @Override
    public boolean isEligible(VoterId voterId) {
        if (voterId == null) return false;
        if (revokedVoters.contains(voterId)) return false;
        return eligibleMap.containsKey(voterId);
    }

    public boolean isExplicitlyRevoked(VoterId voterId) {
        return voterId != null && revokedVoters.contains(voterId);
    }

    @Override
    public void registerEligible(EligibleVoterRecord record) {
        Objects.requireNonNull(record, "record cannot be null");
        revokedVoters.remove(record.voterId());
        eligibleMap.put(record.voterId(), record);
    }

    @Override
    public void removeEligible(VoterId voterId) {
        if (voterId != null) {
            eligibleMap.remove(voterId);
            revokedVoters.add(voterId);
        }
    }

    @Override
    public int size() {
        return eligibleMap.size();
    }

    @Override
    public void clear() {
        eligibleMap.clear();
        revokedVoters.clear();
    }
}
