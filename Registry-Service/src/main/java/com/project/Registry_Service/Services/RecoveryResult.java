package com.project.Registry_Service.Services;

/**
 * Result of a recovery operation attempt.
 * Contains success status, action taken, message, and any error that occurred.
 */
public record RecoveryResult(
    boolean success,
    RecoveryAction actionTaken,
    String message,
    Exception error
) {
    /**
     * Create a successful recovery result
     */
    public static RecoveryResult success(RecoveryAction action, String message) {
        return new RecoveryResult(true, action, message, null);
    }
    
    /**
     * Create a failed recovery result
     */
    public static RecoveryResult failure(String message, Exception error) {
        return new RecoveryResult(false, null, message, error);
    }
    
    /**
     * Create a failed recovery result with action attempted
     */
    public static RecoveryResult failure(RecoveryAction action, String message, Exception error) {
        return new RecoveryResult(false, action, message, error);
    }
}
