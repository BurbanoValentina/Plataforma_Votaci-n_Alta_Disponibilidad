package com.voting.platform.edge.controller;

import com.voting.platform.domain.exception.DuplicateVoteException;
import com.voting.platform.domain.exception.VoterNotEligibleException;
import com.voting.platform.domain.exception.VotingClosedException;
import com.voting.platform.domain.exception.VotingInAuditException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralized REST Exception Handler mapping rich domain exceptions to clean HTTP responses.
 * Returns HTTP 409 Conflict WITH the citizen's original official ticket on duplicate votes.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final String nodeId;

    public GlobalExceptionHandler(@Value("${voting.node-id:voting-edge-1}") String nodeId) {
        this.nodeId = nodeId;
    }

    @ExceptionHandler(DuplicateVoteException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateVote(DuplicateVoteException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getErrorCode());
        body.put("message", ex.getMessage());
        body.put("alreadyVoted", true);
        body.put("ticket", ex.getExistingTicket());
        body.put("nodeId", nodeId);
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .header("X-Node-Id", nodeId)
            .body(body);
    }

    @ExceptionHandler(VoterNotEligibleException.class)
    public ResponseEntity<Map<String, Object>> handleNotEligible(VoterNotEligibleException ex) {
        return buildError(HttpStatus.FORBIDDEN, "VOTER_NOT_ELIGIBLE", ex.getMessage());
    }

    @ExceptionHandler({VotingClosedException.class, VotingInAuditException.class})
    public ResponseEntity<Map<String, Object>> handleLifecycleBlocked(RuntimeException ex) {
        String code = ex instanceof VotingClosedException ? "VOTING_CLOSED" : "VOTING_IN_AUDIT";
        return buildError(HttpStatus.LOCKED, code, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
            .map(err -> err.getField() + ": " + err.getDefaultMessage())
            .collect(Collectors.joining("; "));
        return buildError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", details);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArg(IllegalArgumentException ex) {
        return buildError(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", ex.getMessage());
    }

    private ResponseEntity<Map<String, Object>> buildError(HttpStatus status, String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", code);
        body.put("message", message);
        body.put("nodeId", nodeId);
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(status)
            .header("X-Node-Id", nodeId)
            .body(body);
    }
}
