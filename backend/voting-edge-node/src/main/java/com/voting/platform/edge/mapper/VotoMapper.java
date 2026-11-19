package com.voting.platform.edge.mapper;

import com.voting.platform.domain.exception.DuplicateVoteException;
import com.voting.platform.domain.model.VoteTicketRecord;
import com.voting.platform.edge.dto.response.ConsultaTicketsResponse;
import com.voting.platform.edge.dto.response.VotoAceptadoResponse;
import com.voting.platform.edge.dto.response.VotoDuplicadoResponse;

import java.time.Instant;
import java.util.List;

/**
 * Utility mapper class for transforming voting domain models and exceptions into edge API response DTOs.
 * Follows the clean architecture boundary separation between internal domain records and presentation layer DTOs.
 */
public final class VotoMapper {

    private static final String DEFAULT_VOTE_ACCEPTED_MESSAGE =
            "Voto verificado y registrado exitosamente. Guarde su comprobante electoral.";
    private static final String STATUS_ACCEPTED = "ACCEPTED";
    private static final String DEFAULT_DUPLICATE_CODE = "DUPLICATE_VOTE";
    private static final String DEFAULT_DUPLICATE_MESSAGE = "Voto duplicado detectado.";

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private VotoMapper() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Maps an issued {@link VoteTicketRecord} and serving node identifier into a {@link VotoAceptadoResponse}.
     *
     * @param ticket the verifiable vote ticket record created upon successful vote intake
     * @param nodeId the unique identifier of the edge node that processed the vote
     * @return a populated {@link VotoAceptadoResponse} containing confirmation details and the vote ticket
     */
    public static VotoAceptadoResponse toVotoAceptadoResponse(VoteTicketRecord ticket, String nodeId) {
        return new VotoAceptadoResponse(
                STATUS_ACCEPTED,
                DEFAULT_VOTE_ACCEPTED_MESSAGE,
                nodeId,
                ticket
        );
    }

    /**
     * Maps ticket lookup results for a given citizen ID (cédula) into a {@link ConsultaTicketsResponse}.
     *
     * @param cedula  the citizen identification number queried
     * @param tickets the list of vote tickets registered under the given cédula
     * @param nodeId  the unique identifier of the edge node serving the query
     * @return a populated {@link ConsultaTicketsResponse} with query results and lookup status flag
     */
    public static ConsultaTicketsResponse toConsultaTicketsResponse(String cedula, List<VoteTicketRecord> tickets, String nodeId) {
        boolean found = tickets != null && !tickets.isEmpty();
        return new ConsultaTicketsResponse(
                cedula,
                nodeId,
                found,
                tickets
        );
    }

    /**
     * Maps a {@link DuplicateVoteException} into a {@link VotoDuplicadoResponse} carrying the original ticket.
     * Uses {@link Instant} to capture the exact timestamp of duplicate rejection.
     *
     * @param ex     the duplicate vote domain exception containing the already registered ticket
     * @param nodeId the unique identifier of the edge node that rejected the duplicate submission
     * @return a populated {@link VotoDuplicadoResponse} containing the rejection reason, original ticket, and timestamp
     */
    public static VotoDuplicadoResponse toVotoDuplicadoResponse(DuplicateVoteException ex, String nodeId) {
        String error = (ex != null && ex.getErrorCode() != null) ? ex.getErrorCode() : DEFAULT_DUPLICATE_CODE;
        String message = (ex != null && ex.getMessage() != null) ? ex.getMessage() : DEFAULT_DUPLICATE_MESSAGE;
        VoteTicketRecord existingTicket = (ex != null) ? ex.getExistingTicket() : null;
        String timestamp = Instant.now().toString();

        return new VotoDuplicadoResponse(
                error,
                message,
                true,
                existingTicket,
                nodeId,
                timestamp
        );
    }
}
