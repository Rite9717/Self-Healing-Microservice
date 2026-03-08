package com.project.Registry_Service.Services;

/**
 * Enumeration of recovery actions that can be performed on service instances.
 * Different platforms may support different subsets of these actions.
 */
public enum RecoveryAction {
    /**
     * Restart the instance
     * - Docker: restart container
     * - EC2: reboot instance
     */
    RESTART,
    
    /**
     * Start a stopped instance
     * - Docker: start container
     * - EC2: start instance
     */
    START,
    
    /**
     * Stop a running instance
     * - Docker: stop container
     * - EC2: stop instance
     */
    STOP,
    
    /**
     * Recreate the instance from scratch
     * - Docker: remove and recreate container
     * - EC2: terminate and launch new instance
     */
    RECREATE
}
