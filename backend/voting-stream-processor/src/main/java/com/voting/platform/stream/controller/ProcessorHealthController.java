package com.voting.platform.stream.controller;

import com.voting.platform.stream.service.VoteProcessorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class ProcessorHealthController {

    private final VoteProcessorService voteProcessorService;

    public ProcessorHealthController(VoteProcessorService voteProcessorService) {
        this.voteProcessorService = voteProcessorService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        var snap = voteProcessorService.currentSnapshot();
        return Map.of(
            "status", "UP",
            "service", "voting-stream-processor",
            "totalVotes", snap.totalVotes(),
            "totalRealVotes", snap.totalRealVotes(),
            "totalSimulatedVotes", snap.totalSimulatedVotes(),
            "timestamp", Instant.now().toString()
        );
    }
}
