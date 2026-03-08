package com.project.Registry_Service.Services;

import com.project.Registry_Service.Entity.ServiceInstanceEntity;

import java.util.List;

/**
 * Core abstraction for pluggable recovery mechanisms.
 * Implementations handle platform-specific recovery operations (Docker, EC2, Kubernetes, etc.)
 */
public interface RecoveryStrategy {
    
    /**
     * Get the platform name this strategy handles
     * @return platform identifier (e.g., "docker", "ec2", "kubernetes")
     */
    String getPlatform();
    
    /**
     * Check if this strategy can recover the given instance
     * @param instance the service instance to check
     * @return true if this strategy can handle recovery for the instance
     */
    boolean canRecover(ServiceInstanceEntity instance);
    
    /**
     * Attempt to recover the instance
     * @param instance the service instance to recover
     * @return RecoveryResult containing success status and details
     */
    RecoveryResult recover(ServiceInstanceEntity instance);
    
    /**
     * Get available recovery actions for this platform
     * @return list of supported recovery actions
     */
    List<RecoveryAction> getAvailableActions();
}
