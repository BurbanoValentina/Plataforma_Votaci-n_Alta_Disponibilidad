package com.voting.platform.edge.exception;

import com.voting.platform.domain.exception.DuplicateVoteException;
import com.voting.platform.domain.exception.SecurityThresholdExceededException;
import com.voting.platform.domain.exception.VoterNotEligibleException;
import com.voting.platform.domain.exception.VotingClosedException;
import com.voting.platform.domain.exception.VotingInAuditException;
import com.voting.platform.edge.dto.response.ErrorResponse;
import com.voting.platform.edge.dto.response.VotoDuplicadoResponse;
import com.voting.platform.edge.mapper.AdminMapper;
import com.voting.platform.edge.mapper.VotoMapper;
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
 * - DuplicateVoteException -> HTTP 409 Conflict WITH original official ticket
 * - SecurityThresholdExceededException -> HTTP 403 Forbidden (IP / Cedula blocked for fraud/impersonation)
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final String nodeId;

    public GlobalExceptionHandler(@Value("${voting.node-id:voting-edge-1}") String nodeId) {
        this.nodeId = nodeId;
    }

    @ExceptionHandler(DuplicateVoteException.class)
    public ResponseEntity<VotoDuplicadoResponse> handleDuplicateVote(DuplicateVoteException ex) {
        VotoDuplicadoResponse body = VotoMapper.toVotoDuplicadoResponse(ex, nodeId);
        return ResponseEntity.status(HttpStatus.CONFLICT).header("X-Node-Id", nodeId).body(body);
    }

    @ExceptionHandler(SecurityThresholdExceededException.class)
    public ResponseEntity<Map<String, Object>> handleSecurityBlock(SecurityThresholdExceededException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "SECURITY_BLOCKED");
        body.put("blocked", true);
        body.put("message", ex.getMessage());
        body.put("nodeId", nodeId);
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .header("X-Node-Id", nodeId)
            .body(body);
    }

    @ExceptionHandler(VoterNotEligibleException.class)
    public ResponseEntity<ErrorResponse> handleNotEligible(VoterNotEligibleException ex) {
        return buildError(HttpStatus.FORBIDDEN, "VOTER_NOT_ELIGIBLE", ex.getMessage());
    }

    @ExceptionHandler({VotingClosedException.class, VotingInAuditException.class})
    public ResponseEntity<ErrorResponse> handleLifecycleBlocked(RuntimeException ex) {
        String code = ex instanceof VotingClosedException ? "VOTING_CLOSED" : "VOTING_IN_AUDIT";
        return buildError(HttpStatus.LOCKED, code, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
            .map(err -> err.getField() + ": " + err.getDefaultMessage())
            .collect(Collectors.joining("; "));
        return buildError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", details);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArg(IllegalArgumentException ex) {
        return buildError(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", ex.getMessage());
    }

    private ResponseEntity<ErrorResponse> buildError(HttpStatus status, String code, String message) {
        ErrorResponse body = AdminMapper.toErrorResponse(code, message, nodeId);
        return ResponseEntity.status(status).header("X-Node-Id", nodeId).body(body);
    }
}
