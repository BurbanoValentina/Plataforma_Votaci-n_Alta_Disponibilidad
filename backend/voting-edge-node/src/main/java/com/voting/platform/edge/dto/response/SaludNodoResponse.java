package com.voting.platform.edge.dto.response;

import java.io.Serializable;

/**
 * Data Transfer Object representing the health, telemetry, and operational status of an edge node.
 * Used by load balancers (e.g. AWS ALB), monitoring probes, and administrative consoles.
 */
public class SaludNodoResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Overall operational health status of the node (e.g. "UP", "DOWN", "DEGRADED").
     */
    private String status;

    /**
     * Unique identifier assigned to this edge node instance.
     */
    private String nodeId;

    /**
     * Cloud availability zone in which this edge node is deployed (e.g. "us-east-1a").
     */
    private String availabilityZone;

    /**
     * Number of eligible voters currently synchronized and held in the local KTable/in-memory cache.
     */
    private int ktableEligibleCount;

    /**
     * Current electoral lifecycle state (e.g. "PRE_ELECTION", "OPEN", "AUDIT_LOCKED", "CLOSED").
     */
    private String lifecycleState;

    /**
     * ISO-8601 formatted timestamp of the health check inspection.
     */
    private String timestamp;

    /**
     * Default no-argument constructor required for serialization frameworks.
     */
    public SaludNodoResponse() {
    }

    /**
     * All-arguments constructor to initialize all health telemetry fields.
     *
     * @param status              node operational health status
     * @param nodeId              node identifier
     * @param availabilityZone    deployment availability zone
     * @param ktableEligibleCount count of eligible voters loaded in memory
     * @param lifecycleState      active electoral lifecycle state
     * @param timestamp           ISO-8601 formatted timestamp
     */
    public SaludNodoResponse(String status, String nodeId, String availabilityZone, int ktableEligibleCount, String lifecycleState, String timestamp) {
        this.status = status;
        this.nodeId = nodeId;
        this.availabilityZone = availabilityZone;
        this.ktableEligibleCount = ktableEligibleCount;
        this.lifecycleState = lifecycleState;
        this.timestamp = timestamp;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getAvailabilityZone() {
        return availabilityZone;
    }

    public void setAvailabilityZone(String availabilityZone) {
        this.availabilityZone = availabilityZone;
    }

    public int getKtableEligibleCount() {
        return ktableEligibleCount;
    }

    public void setKtableEligibleCount(int ktableEligibleCount) {
        this.ktableEligibleCount = ktableEligibleCount;
    }

    public String getLifecycleState() {
        return lifecycleState;
    }

    public void setLifecycleState(String lifecycleState) {
        this.lifecycleState = lifecycleState;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
