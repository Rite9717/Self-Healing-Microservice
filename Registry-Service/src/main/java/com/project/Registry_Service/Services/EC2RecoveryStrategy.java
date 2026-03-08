package com.project.Registry_Service.Services;

import com.project.Registry_Service.Entity.ServiceInstanceEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@ConditionalOnProperty(name = "registry.recovery.ec2.enabled", havingValue = "true")
public class EC2RecoveryStrategy implements RecoveryStrategy {
    
    private final Ec2Client ec2Client;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF_MS = 1000;
    
    public EC2RecoveryStrategy(Ec2Client ec2Client) {
        this.ec2Client = ec2Client;
    }

    @Override
    public String getPlatform() {
        return "ec2";
    }

    @Override
    public boolean canRecover(ServiceInstanceEntity instance) {
        return "ec2".equals(instance.getService().getPlatform()) 
            && instance.getEc2InstanceId() != null 
            && !instance.getEc2InstanceId().isEmpty();
    }

    @Override
    public RecoveryResult recover(ServiceInstanceEntity instance) {
        String instanceId = instance.getEc2InstanceId();
        
        if (instanceId == null || instanceId.isEmpty()) {
            String message = "Cannot recover - no EC2 instance ID for instance: " + 
                instance.getHost() + ":" + instance.getPort();
            log.error(message);
            return RecoveryResult.failure(message, null);
        }
        
        try {
            // Get current instance state
            InstanceStateName state = getInstanceState(instanceId);
            RecoveryAction action = determineAction(state);
            
            log.info("EC2 instance {} is in state {}. Performing action: {}", 
                instanceId, state, action);
            
            // Execute recovery action with retry logic
            return executeWithRetry(instanceId, action);
            
        } catch (Ec2Exception e) {
            String message = "EC2 recovery failed for instance " + instanceId + ": " + 
                e.awsErrorDetails().errorMessage();
            log.error(message, e);
            
            // Check if error is retryable
            if (isRetryableError(e)) {
                return RecoveryResult.failure(message, e);
            } else {
                // Non-retryable error - should quarantine
                return RecoveryResult.failure(message + " (non-retryable)", e);
            }
        } catch (Exception e) {
            String message = "Unexpected error during EC2 recovery for instance " + instanceId;
            log.error(message, e);
            return RecoveryResult.failure(message, e);
        }
    }

    @Override
    public List<RecoveryAction> getAvailableActions() {
        return Arrays.asList(RecoveryAction.RESTART, RecoveryAction.START, RecoveryAction.STOP);
    }

    /**
     * Get the current state of an EC2 instance
     */
    private InstanceStateName getInstanceState(String instanceId) {
        DescribeInstancesRequest request = DescribeInstancesRequest.builder()
                .instanceIds(instanceId)
                .build();
        
        DescribeInstancesResponse response = ec2Client.describeInstances(request);
        
        if (response.reservations().isEmpty() || 
            response.reservations().get(0).instances().isEmpty()) {
            throw new IllegalStateException("EC2 instance not found: " + instanceId);
        }
        
        return response.reservations().get(0).instances().get(0).state().name();
    }

    /**
     * Determine the appropriate recovery action based on instance state
     */
    private RecoveryAction determineAction(InstanceStateName state) {
        return switch (state) {
            case STOPPED, STOPPING -> RecoveryAction.START;
            case RUNNING -> RecoveryAction.RESTART;
            case PENDING -> RecoveryAction.RESTART; // Wait for it to start, then restart
            default -> RecoveryAction.RESTART;
        };
    }

    /**
     * Execute recovery action with exponential backoff retry logic
     */
    private RecoveryResult executeWithRetry(String instanceId, RecoveryAction action) {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                switch (action) {
                    case START -> {
                        startInstance(instanceId);
                        return RecoveryResult.success(RecoveryAction.START, 
                            "EC2 instance started successfully");
                    }
                    case RESTART -> {
                        rebootInstance(instanceId);
                        return RecoveryResult.success(RecoveryAction.RESTART, 
                            "EC2 instance rebooted successfully");
                    }
                    case STOP -> {
                        stopInstance(instanceId);
                        return RecoveryResult.success(RecoveryAction.STOP, 
                            "EC2 instance stopped successfully");
                    }
                    default -> {
                        return RecoveryResult.failure("Unsupported recovery action: " + action, null);
                    }
                }
            } catch (Ec2Exception e) {
                lastException = e;
                
                if (!isRetryableError(e)) {
                    // Non-retryable error, fail immediately
                    String message = "Non-retryable EC2 error: " + e.awsErrorDetails().errorMessage();
                    log.error(message);
                    return RecoveryResult.failure(action, message, e);
                }
                
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    long backoffMs = INITIAL_BACKOFF_MS * (long) Math.pow(2, attempt - 1);
                    log.warn("EC2 operation failed (attempt {}/{}), retrying in {}ms: {}", 
                        attempt, MAX_RETRY_ATTEMPTS, backoffMs, e.awsErrorDetails().errorMessage());
                    
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return RecoveryResult.failure(action, "Recovery interrupted", ie);
                    }
                }
            }
        }
        
        String message = "EC2 recovery failed after " + MAX_RETRY_ATTEMPTS + " attempts";
        log.error(message);
        return RecoveryResult.failure(action, message, lastException);
    }

    /**
     * Start a stopped EC2 instance
     */
    private void startInstance(String instanceId) {
        StartInstancesRequest request = StartInstancesRequest.builder()
                .instanceIds(instanceId)
                .build();
        
        ec2Client.startInstances(request);
        log.info("Started EC2 instance: {}", instanceId);
    }

    /**
     * Reboot a running EC2 instance
     */
    private void rebootInstance(String instanceId) {
        RebootInstancesRequest request = RebootInstancesRequest.builder()
                .instanceIds(instanceId)
                .build();
        
        ec2Client.rebootInstances(request);
        log.info("Rebooted EC2 instance: {}", instanceId);
    }

    /**
     * Stop a running EC2 instance
     */
    private void stopInstance(String instanceId) {
        StopInstancesRequest request = StopInstancesRequest.builder()
                .instanceIds(instanceId)
                .build();
        
        ec2Client.stopInstances(request);
        log.info("Stopped EC2 instance: {}", instanceId);
    }

    /**
     * Check if an EC2 error is retryable
     */
    private boolean isRetryableError(Ec2Exception e) {
        String errorCode = e.awsErrorDetails().errorCode();
        
        // Common retryable error codes
        return errorCode != null && (
            errorCode.equals("RequestLimitExceeded") ||
            errorCode.equals("ServiceUnavailable") ||
            errorCode.equals("InternalError") ||
            errorCode.equals("Throttling")
        );
    }
}
