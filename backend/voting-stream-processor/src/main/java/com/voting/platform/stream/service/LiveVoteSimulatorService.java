package com.voting.platform.stream.service;

import com.voting.platform.domain.model.CandidateCatalog;
import com.voting.platform.domain.model.City;
import com.voting.platform.domain.model.ElectionType;
import com.voting.platform.domain.model.VoteRecord;
import com.voting.platform.domain.model.VoteSource;
import com.voting.platform.domain.model.VoteTicketCode;
import com.voting.platform.domain.port.VoteEventPublisher;
import com.voting.platform.stream.repository.PostgresStreamRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Real-Time Electoral Traffic Simulator.
 * Emits realistic SIMULATED votes for both NACIONAL and REGIONAL elections through the
 * exact same Kafka -> Stream Processor -> RabbitMQ -> PostgreSQL pipeline so administrators
 * can observe live streaming telemetry and compare SIMULATED vs REAL citizen votes.
 */
@Service
public class LiveVoteSimulatorService {

    private static final Logger log = LoggerFactory.getLogger(LiveVoteSimulatorService.class);

    private static final List<String> CITIES = City.allCityNames();
    private static final List<String> NATIONAL_CANDIDATES = CandidateCatalog.byElectionType(ElectionType.NACIONAL)
        .stream().map(CandidateCatalog.CandidateProfile::id).toList();
    private static final List<String> REGIONAL_CANDIDATES = CandidateCatalog.byElectionType(ElectionType.REGIONAL)
        .stream().map(CandidateCatalog.CandidateProfile::id).toList();
    private static final String[] SIM_NODES = {"aws-node-1-us-east-1a", "aws-node-2-us-east-1b"};

    private final VoteEventPublisher votePublisher;
    private final PostgresStreamRepository postgresRepository;
    private final AtomicLong simSequence = new AtomicLong(100000);
    private volatile long lastRunMs = 0L;

    public LiveVoteSimulatorService(
        VoteEventPublisher votePublisher,
        PostgresStreamRepository postgresRepository
    ) {
        this.votePublisher = Objects.requireNonNull(votePublisher, "votePublisher cannot be null");
        this.postgresRepository = Objects.requireNonNull(postgresRepository, "postgresRepository cannot be null");
    }

    @Scheduled(fixedDelay = 600)
    public void tick() {
        PostgresStreamRepository.SimulatorControl control = postgresRepository.readSimulatorControl();

        if (control.botAttackRequested()) {
            postgresRepository.clearBotAttackFlag();
            triggerBotAttackSimulation();
        }

        if (!control.simulatorEnabled() || !"HABILITADO".equalsIgnoreCase(control.lifecycleState())) {
            return;
        }

        long now = System.currentTimeMillis();
        int rateMs = Math.max(400, control.simulatorRateMs());
        if (now - lastRunMs < rateMs) {
            return;
        }
        lastRunMs = now;

        int batch = Math.min(10, Math.max(1, control.simulatorBatchSize()));
        for (int i = 0; i < batch; i++) {
            emitSimulatedVote();
        }
    }

    private void emitSimulatedVote() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        ElectionType electionType = rnd.nextBoolean() ? ElectionType.NACIONAL : ElectionType.REGIONAL;
        List<String> pool = electionType == ElectionType.NACIONAL ? NATIONAL_CANDIDATES : REGIONAL_CANDIDATES;

        // Weighted realistic electoral distribution so live charts look authentic
        int idx = weightedCandidateIndex(rnd, pool.size());
        String candidateId = pool.get(idx);
        String city = CITIES.get(rnd.nextInt(CITIES.size()));
        String simVoterId = "SIM-" + simSequence.incrementAndGet();
        String ip = "10.24." + rnd.nextInt(1, 250) + "." + rnd.nextInt(1, 250);
        String node = SIM_NODES[rnd.nextInt(SIM_NODES.length)];
        String ticketCode = VoteTicketCode.generate(electionType).value();

        VoteRecord vote = VoteRecord.create(
            simVoterId,
            candidateId,
            city,
            null,
            electionType.name(),
            VoteSource.SIMULATED.name(),
            ip,
            ticketCode,
            node,
            Instant.now()
        );
        votePublisher.publishRawVote(vote);
        postgresRepository.recordNodeSimulatedVote(node);
    }

    private int weightedCandidateIndex(ThreadLocalRandom rnd, int size) {
        int roll = rnd.nextInt(100);
        if (roll < 36) return 0;
        if (roll < 66) return Math.min(1, size - 1);
        if (roll < 86) return Math.min(2, size - 1);
        return Math.min(3, size - 1);
    }

    public void triggerBotAttackSimulation() {
        String attackerIp = "185.220.101." + ThreadLocalRandom.current().nextInt(10, 240);
        log.warn("Executing simulated Bot Attack burst (18 votes) from IP {}", attackerIp);
        for (int i = 0; i < 18; i++) {
            String simVoter = "BOT-" + simSequence.incrementAndGet();
            String candidateId = NATIONAL_CANDIDATES.get(0);
            VoteRecord vote = VoteRecord.create(
                simVoter,
                candidateId,
                "Bogotá",
                "CENTRO",
                ElectionType.NACIONAL.name(),
                VoteSource.SIMULATED.name(),
                attackerIp,
                VoteTicketCode.generate(ElectionType.NACIONAL).value(),
                SIM_NODES[i % SIM_NODES.length],
                Instant.now()
            );
            votePublisher.publishRawVote(vote);
        }
    }
}
