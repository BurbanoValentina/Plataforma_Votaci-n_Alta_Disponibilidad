package com.voting.platform.edge.dto.response;

import java.io.Serializable;

/**
 * Data Transfer Object representing the response for a successful administrative login attempt.
 * Encapsulates authentication status, user identity, assigned role, session token,
 * serving node identifier, and operation timestamp.
 */
public class LoginAdminResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Whether the administrator was successfully authenticated.
     */
    private boolean authenticated;

    /**
     * The authenticated user's email or username.
     */
    private String user;

    /**
     * The administrative authorization role (e.g. SUPER_ADMIN, AUDITOR).
     */
    private String role;

    /**
     * Bearer or session token issued for subsequent administrative API requests.
     */
    private String token;

    /**
     * Identifier of the edge node instance that served and authorized this request.
     */
    private String servedByNode;

    /**
     * ISO-8601 formatted timestamp indicating when the login response was generated.
     */
    private String timestamp;

    /**
     * Default no-argument constructor required for serialization and frameworks.
     */
    public LoginAdminResponse() {
    }

    /**
     * All-arguments constructor to initialize all response fields.
     *
     * @param authenticated authentication success flag
     * @param user          authenticated user identifier
     * @param role          assigned authorization role
     * @param token         authentication session token
     * @param servedByNode  edge node identifier
     * @param timestamp     ISO-8601 response timestamp
     */
    public LoginAdminResponse(boolean authenticated, String user, String role, String token, String servedByNode, String timestamp) {
        this.authenticated = authenticated;
        this.user = user;
        this.role = role;
        this.token = token;
        this.servedByNode = servedByNode;
        this.timestamp = timestamp;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public boolean getAuthenticated() {
        return authenticated;
    }

    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getServedByNode() {
        return servedByNode;
    }

    public void setServedByNode(String servedByNode) {
        this.servedByNode = servedByNode;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
