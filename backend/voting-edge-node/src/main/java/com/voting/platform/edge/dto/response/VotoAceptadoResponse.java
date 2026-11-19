package com.voting.platform.edge.dto.response;

import com.voting.platform.domain.model.VoteTicketRecord;

import java.io.Serializable;

/**
 * Data Transfer Object representing a successfully accepted and registered citizen vote.
 * Contains confirmation status ("ACCEPTED"), informational message, serving node identifier,
 * and the official cryptographic VoteTicketRecord value object.
 */
public class VotoAceptadoResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Processing status of the vote, always "ACCEPTED" upon successful intake.
     */
    private String status = "ACCEPTED";

    /**
     * User-facing confirmation message with instructions to preserve the receipt.
     */
    private String message;

    /**
     * Identifier of the edge node that verified and processed the vote submission.
     */
    private String nodeId;

    /**
     * Official verifiable vote ticket record containing cryptographic hash, timestamps, and receipt details.
     */
    private VoteTicketRecord ticket;

    /**
     * Default no-argument constructor initializing default status to "ACCEPTED".
     */
    public VotoAceptadoResponse() {
        this.status = "ACCEPTED";
    }

    /**
     * All-arguments constructor to initialize all response fields.
     * Ensures status defaults to "ACCEPTED" if null or blank.
     *
     * @param status  vote registration status (defaults to "ACCEPTED")
     * @param message confirmation message
     * @param nodeId  serving edge node identifier
     * @param ticket  verifiable vote ticket record
     */
    public VotoAceptadoResponse(String status, String message, String nodeId, VoteTicketRecord ticket) {
        this.status = (status != null && !status.isBlank()) ? status : "ACCEPTED";
        this.message = message;
        this.nodeId = nodeId;
        this.ticket = ticket;
    }

    /**
     * Convenience constructor initializing status to "ACCEPTED" automatically.
     *
     * @param message confirmation message
     * @param nodeId  serving edge node identifier
     * @param ticket  verifiable vote ticket record
     */
    public VotoAceptadoResponse(String message, String nodeId, VoteTicketRecord ticket) {
        this("ACCEPTED", message, nodeId, ticket);
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = (status != null && !status.isBlank()) ? status : "ACCEPTED";
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

    public VoteTicketRecord getTicket() {
        return ticket;
    }

    public void setTicket(VoteTicketRecord ticket) {
        this.ticket = ticket;
    }
}
