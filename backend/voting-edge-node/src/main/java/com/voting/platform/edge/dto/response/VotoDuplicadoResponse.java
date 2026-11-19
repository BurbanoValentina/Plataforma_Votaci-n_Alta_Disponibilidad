package com.voting.platform.edge.dto.response;

import com.voting.platform.domain.dto.response.VoteTicketRecord;

import java.io.Serializable;

/**
 * Data Transfer Object returned when a citizen attempts to cast a duplicate vote with the same cédula.
 * Contains error details, duplicate notification message, confirmation that the citizen already voted,
 * the original official VoteTicketRecord, serving node identifier, and timestamp.
 */
public class VotoDuplicadoResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Machine-readable error code (e.g. DUPLICATE_VOTE_DETECTED).
     */
    private String error;

    /**
     * Informational message explaining the duplicate attempt and providing the original ticket.
     */
    private String message;

    /**
     * Flag indicating that a vote was already registered for this citizen identity, always true.
     */
    private boolean alreadyVoted = true;

    /**
     * The official verifiable vote ticket from the citizen's previously registered vote.
     */
    private VoteTicketRecord ticket;

    /**
     * Identifier of the edge node that detected and handled the duplicate vote attempt.
     */
    private String nodeId;

    /**
     * ISO-8601 formatted timestamp of the duplicate vote rejection.
     */
    private String timestamp;

    /**
     * Default no-argument constructor initializing alreadyVoted to true.
     */
    public VotoDuplicadoResponse() {
        this.alreadyVoted = true;
    }

    /**
     * All-arguments constructor to fully initialize all duplicate vote response fields.
     * Enforces alreadyVoted to true.
     *
     * @param error        error categorization code
     * @param message      duplicate rejection message
     * @param alreadyVoted indicator that citizen already voted (always true)
     * @param ticket       existing official vote ticket record
     * @param nodeId       edge node identifier
     * @param timestamp    ISO-8601 formatted timestamp
     */
    public VotoDuplicadoResponse(String error, String message, boolean alreadyVoted, VoteTicketRecord ticket, String nodeId, String timestamp) {
        this.error = error;
        this.message = message;
        this.alreadyVoted = true;
        this.ticket = ticket;
        this.nodeId = nodeId;
        this.timestamp = timestamp;
    }

    /**
     * Convenience constructor omitting the redundant boolean flag and defaulting alreadyVoted to true.
     *
     * @param error     error categorization code
     * @param message   duplicate rejection message
     * @param ticket    existing official vote ticket record
     * @param nodeId    edge node identifier
     * @param timestamp ISO-8601 formatted timestamp
     */
    public VotoDuplicadoResponse(String error, String message, VoteTicketRecord ticket, String nodeId, String timestamp) {
        this(error, message, true, ticket, nodeId, timestamp);
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

    public boolean isAlreadyVoted() {
        return alreadyVoted;
    }

    public boolean getAlreadyVoted() {
        return alreadyVoted;
    }

    public void setAlreadyVoted(boolean alreadyVoted) {
        this.alreadyVoted = alreadyVoted;
    }

    public VoteTicketRecord getTicket() {
        return ticket;
    }

    public void setTicket(VoteTicketRecord ticket) {
        this.ticket = ticket;
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
