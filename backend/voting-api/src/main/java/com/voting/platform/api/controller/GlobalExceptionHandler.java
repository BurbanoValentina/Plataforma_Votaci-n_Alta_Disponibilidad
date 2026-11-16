package com.voting.platform.api.controller;

import com.voting.platform.domain.exception.DomainException;
import com.voting.platform.domain.exception.VoterNotEligibleException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Global Exception Handler translating domain and validation exceptions
 * into structured HTTP status codes.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(VoterNotEligibleException.class)
    public ResponseEntity<Map<String, Object>> handleVoterNotEligible(VoterNotEligibleException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", "FORBIDDEN");
        body.put("voterId", ex.getVoterId());
        body.put("message", ex.getMessage());
        body.put("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", "BAD_REQUEST");
        body.put("message", "Validation failed for one or more fields");

        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        body.put("errors", fieldErrors);
        body.put("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", "BAD_REQUEST");
        body.put("message", ex.getMessage());
        body.put("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<Map<String, Object>> handleDomainException(DomainException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", "UNPROCESSABLE_ENTITY");
        body.put("message", ex.getMessage());
        body.put("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }
}
