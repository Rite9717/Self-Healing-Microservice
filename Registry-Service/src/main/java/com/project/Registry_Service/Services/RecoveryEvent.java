package com.project.Registry_Service.Services;

import com.project.Registry_Service.Entity.ServiceInstanceEntity;

import java.time.LocalDateTime;

/**
 * Event published when a recovery operation completes (success or failure).
 * Contains instance details, recovery action, outcome, and timestamp.
 */
public record RecoveryEvent(
    Long instanceId,
    String serviceName,
    String host,
    int port,
    RecoveryAction action,
    boolean success,
    String message,
    LocalDateTime timestamp
) {
    /**
     * Create a RecoveryEvent from a ServiceInstanceEntity
     */
    public RecoveryEvent(ServiceInstanceEntity instance, RecoveryAction action, 
                        boolean success, String message) {
        this(
            instance.getId(),
            instance.getService().getName(),
            instance.getHost(),
            instance.getPort(),
            action,
            success,
            message,
            LocalDateTime.now()
        );
    }
}
