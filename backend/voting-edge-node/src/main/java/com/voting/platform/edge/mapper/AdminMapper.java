package com.voting.platform.edge.mapper;

import com.voting.platform.edge.dto.response.ErrorResponse;
import com.voting.platform.edge.dto.response.LoginAdminResponse;
import com.voting.platform.edge.dto.response.SaludNodoResponse;

import java.time.Instant;

/**
 * Utility mapper class for transforming administrative, error, and health telemetry data into edge API response DTOs.
 * Provides static mapping methods conforming to clean architecture separation of concerns.
 */
public final class AdminMapper {

    private static final String STATUS_UP = "UP";

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private AdminMapper() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Maps authenticated administrative user details and session token into a {@link LoginAdminResponse}.
     * Uses {@link Instant} to generate the authentication timestamp.
     *
     * @param email  the authenticated administrator email or username
     * @param role   the administrative authorization role (e.g., SUPER_ADMIN)
     * @param token  the issued session authorization token
     * @param nodeId the unique identifier of the edge node that authorized the login
     * @return a populated {@link LoginAdminResponse} with authentication status and timestamp
     */
    public static LoginAdminResponse toLoginResponse(String email, String role, String token, String nodeId) {
        return new LoginAdminResponse(
                true,
                email,
                role,
                token,
                nodeId,
                Instant.now().toString()
        );
    }

    /**
     * Maps an error code, diagnostic message, and node identifier into a standardized {@link ErrorResponse}.
     * Uses {@link Instant} to capture the exact timestamp of the error occurrence.
     *
     * @param errorCode the machine-readable error classification code
     * @param message   the descriptive explanation of the error
     * @param nodeId    the unique identifier of the edge node that produced or caught the error
     * @return a populated {@link ErrorResponse} containing diagnostic details and timestamp
     */
    public static ErrorResponse toErrorResponse(String errorCode, String message, String nodeId) {
        return new ErrorResponse(
                errorCode,
                message,
                nodeId,
                Instant.now().toString()
        );
    }

    /**
     * Maps edge node telemetry and operational status into a {@link SaludNodoResponse} for health checks.
     * Uses {@link Instant} to timestamp the health inspection.
     *
     * @param nodeId         the unique identifier of the edge node instance
     * @param az             the availability zone where the node is deployed (e.g. us-east-1a)
     * @param ktableSize     the current count of eligible voters loaded in local memory
     * @param lifecycleState the current electoral lifecycle state (e.g. OPEN, CLOSED)
     * @return a populated {@link SaludNodoResponse} indicating node operational health ("UP")
     */
    public static SaludNodoResponse toSaludNodoResponse(String nodeId, String az, int ktableSize, String lifecycleState) {
        return new SaludNodoResponse(
                STATUS_UP,
                nodeId,
                az,
                ktableSize,
                lifecycleState,
                Instant.now().toString()
        );
    }
}
