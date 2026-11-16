package com.voting.platform.api.controller;

import com.voting.platform.api.model.VoteResponseRecord;
import com.voting.platform.api.service.VotingService;
import com.voting.platform.domain.model.VoteRequestRecord;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * Public REST Controller gateway for submitting votes.
 * Applies Bean Validation and constructor injection.
 */
@RestController
@RequestMapping("/vote")
public class VotingController {

    private final VotingService votingService;

    public VotingController(VotingService votingService) {
        this.votingService = Objects.requireNonNull(votingService, "votingService cannot be null");
    }

    @PostMapping
    public ResponseEntity<VoteResponseRecord> submitVote(@Valid @RequestBody VoteRequestRecord request) {
        VoteResponseRecord response = votingService.castVote(request);
        return ResponseEntity.ok(response);
    }
}
