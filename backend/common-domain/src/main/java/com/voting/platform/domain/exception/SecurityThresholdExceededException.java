package com.voting.platform.domain.exception;

import com.voting.platform.domain.model.IpAddress;

/**
 * Thrown when an IP address triggers anti-fraud thresholds and is blocked.
 */
public class SecurityThresholdExceededException extends DomainException {

    private final String ipAddress;

    public SecurityThresholdExceededException(IpAddress ipAddress, String message) {
        super(String.format("Security alert for IP '%s': %s", ipAddress.value(), message));
        this.ipAddress = ipAddress.value();
    }

    public String getIpAddress() {
        return ipAddress;
    }
}
