package com.voting.platform.edge.dto.response;

import com.voting.platform.domain.dto.response.VoteTicketRecord;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Transfer Object representing the result of querying vote tickets associated with a citizen ID (cédula).
 * Encapsulates the citizen identifier, serving node, found status flag, and list of verifiable tickets.
 */
public class ConsultaTicketsResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Identification number (cédula) of the citizen queried.
     */
    private String cedula;

    /**
     * Identifier of the edge node answering the query.
     */
    private String nodeId;

    /**
     * Whether any vote tickets were found for the queried citizen ID.
     */
    private boolean found;

    /**
     * List of official vote tickets registered for this citizen across elections.
     */
    private List<VoteTicketRecord> tickets;

    /**
     * Default no-argument constructor initializing an empty tickets list.
     */
    public ConsultaTicketsResponse() {
        this.tickets = new ArrayList<>();
    }

    /**
     * All-arguments constructor to fully initialize the ticket query response.
     *
     * @param cedula  citizen identification number
     * @param nodeId  serving edge node identifier
     * @param found   whether tickets were located
     * @param tickets list of found vote ticket records
     */
    public ConsultaTicketsResponse(String cedula, String nodeId, boolean found, List<VoteTicketRecord> tickets) {
        this.cedula = cedula;
        this.nodeId = nodeId;
        this.found = found;
        this.tickets = (tickets != null) ? tickets : new ArrayList<>();
    }

    public String getCedula() {
        return cedula;
    }

    public void setCedula(String cedula) {
        this.cedula = cedula;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public boolean isFound() {
        return found;
    }

    public boolean getFound() {
        return found;
    }

    public void setFound(boolean found) {
        this.found = found;
    }

    public List<VoteTicketRecord> getTickets() {
        return tickets;
    }

    public void setTickets(List<VoteTicketRecord> tickets) {
        this.tickets = (tickets != null) ? tickets : new ArrayList<>();
    }
}
