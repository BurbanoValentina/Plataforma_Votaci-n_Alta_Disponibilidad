package com.voting.platform.edge.dto.response;

import java.io.Serializable;
import java.time.Instant;

/**
 * Standardized Data Transfer Object for error and exception responses across the edge node.
 * Provides error code categorization, human-readable description, emitting node identifier,
 * and generation timestamp.
 */
public class ErrorResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Machine-readable error code or exception classification (e.g. VALIDATION_ERROR, SECURITY_BLOCKED).
     */
    private String error;

    /**
     * Human-readable diagnostic or informational message explaining the cause of failure.
     */
    private String message;

    /**
     * Identifier of the edge node instance where the error was caught and processed.
     */
    private String nodeId;

    /**
     * ISO-8601 formatted timestamp of when the error occurred.
     */
    private String timestamp;

    /**
     * Default no-argument constructor required for serialization frameworks.
     */
    public ErrorResponse() {
    }

    /**
     * All-arguments constructor to fully initialize the error response payload.
     *
     * @param error     error classification code
     * @param message   descriptive message
     * @param nodeId    edge node identifier
     * @param timestamp ISO-8601 formatted timestamp
     */
    public ErrorResponse(String error, String message, String nodeId, String timestamp) {
        this.error = error;
        this.message = message;
        this.nodeId = nodeId;
        this.timestamp = timestamp;
    }

    /**
     * Static factory method to instantiate an {@link ErrorResponse} with the current UTC timestamp.
     *
     * @param error   the error code or category
     * @param message the detailed human-readable message
     * @param nodeId  the identifier of the edge node that produced this error
     * @return a new fully-populated ErrorResponse instance
     */
    public static ErrorResponse of(String error, String message, String nodeId) {
        return new ErrorResponse(error, message, nodeId, Instant.now().toString());
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
