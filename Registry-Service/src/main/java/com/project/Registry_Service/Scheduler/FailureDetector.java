package com.project.Registry_Service.Scheduler;

import com.project.Registry_Service.Entity.InstanceState;
import com.project.Registry_Service.Entity.ServiceInstanceEntity;
import com.project.Registry_Service.Repository.ServiceInstanceRepository;
import com.project.Registry_Service.Services.HealthCheckService;
import com.project.Registry_Service.Services.MetricsService;
import com.project.Registry_Service.Services.RecoveryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@EnableScheduling
@Component
public class FailureDetector
{
    private final ServiceInstanceRepository serviceInstanceRepo;
    private final RecoveryService recovery;
    private final MetricsService metricsService;
    private final HealthCheckService healthCheckService;
    
    private static final long DEFAULT_HEARTBEAT_THRESHOLD_MS = 30_000;
    private static final long GRACE_PERIOD_MS = 60_000;
    private static final int SUSPECT_THRESHOLD = 2;
    private static final int UNRESPONSIVE_THRESHOLD = 4;
    
    public FailureDetector(ServiceInstanceRepository serviceInstanceRepo, 
                          RecoveryService recovery,
                          MetricsService metricsService,
                          HealthCheckService healthCheckService)
    {
        this.serviceInstanceRepo = serviceInstanceRepo;
        this.recovery = recovery;
        this.metricsService = metricsService;
        this.healthCheckService = healthCheckService;
    }

    @Scheduled(fixedDelay = 10_000)
    public void detectFailures()
    {
        long now = System.currentTimeMillis();
        
        for (ServiceInstanceEntity instance : serviceInstanceRepo.findAll())
        {
            // Skip instances in grace period
            if (isInGracePeriod(instance, now)) {
                log.trace("Instance {} is in grace period, skipping health check", instance.getId());
                continue;
            }
            
            long threshold = getHeartbeatThreshold(instance);
            long silence = now - instance.getLastHeartBeat();
            
            // Perform health check if configured
            boolean healthCheckPassed = performHealthCheck(instance);
            
            switch (instance.getState())
            {
                case UP -> {
                    if (silence > threshold && !healthCheckPassed) {
                        instance.setMissedHeartBeats(1);
                        instance.setState(InstanceState.SUSPECT);
                        log.info("Instance {} transitioned to SUSPECT (silence: {}ms, threshold: {}ms)", 
                            instance.getId(), silence, threshold);
                    }
                }

                case SUSPECT -> {
                    if (silence <= threshold || healthCheckPassed) {
                        instance.setMissedHeartBeats(0);
                        instance.setState(InstanceState.UP);
                        metricsService.recordServiceUp(instance);
                        log.info("Instance {} recovered to UP state", instance.getId());
                    } else {
                        int missed = instance.getMissedHeartBeats() + 1;
                        instance.setMissedHeartBeats(missed);
                        if (missed >= UNRESPONSIVE_THRESHOLD) {
                            instance.setState(InstanceState.UNRESPONSIVE);
                            metricsService.recordFailure(instance);
                            log.warn("Instance {} transitioned to UNRESPONSIVE (missed: {})", 
                                instance.getId(), missed);
                        }
                    }
                }

                case UNRESPONSIVE -> {
                    if (silence <= threshold || healthCheckPassed) {
                        instance.setMissedHeartBeats(0);
                        instance.setState(InstanceState.UP);
                        metricsService.recordServiceUp(instance);
                        log.info("Instance {} recovered to UP state from UNRESPONSIVE", instance.getId());
                    } else if (canAttemptRecovery(instance, now)) {
                        recovery.recover(instance);
                    }
                }

                case QUARANTINED -> {
                    if (instance.getQuarantineUntilTimestamp() != null 
                        && now >= instance.getQuarantineUntilTimestamp()) {
                        instance.setState(InstanceState.UNRESPONSIVE);
                        log.info("Instance {} released from quarantine", instance.getId());
                    }
                }
            }
            
            serviceInstanceRepo.save(instance);
        }
    }

    /**
     * Check if instance is in grace period (newly registered)
     */
    private boolean isInGracePeriod(ServiceInstanceEntity instance, long now) {
        if (instance.getCreatedAt() == null) {
            return false;
        }
        
        long age = now - instance.getCreatedAt().getTime();
        return age < GRACE_PERIOD_MS;
    }

    /**
     * Get heartbeat threshold for instance (custom or default)
     */
    private long getHeartbeatThreshold(ServiceInstanceEntity instance) {
        Long customThreshold = instance.getService().getHeartbeatThresholdMs();
        return customThreshold != null ? customThreshold : DEFAULT_HEARTBEAT_THRESHOLD_MS;
    }

    /**
     * Perform health check if configured
     */
    private boolean performHealthCheck(ServiceInstanceEntity instance) {
        if (instance.getHealthPath() == null || instance.getHealthPath().isEmpty()) {
            return false; // No health check configured, rely on heartbeat only
        }
        
        return healthCheckService.check(instance);
    }

    private boolean canAttemptRecovery(ServiceInstanceEntity instance, long now)
    {
        if (instance.getQuarantineUntilTimestamp() != null 
            && now < instance.getQuarantineUntilTimestamp()) {
            return false;
        }
        
        if (instance.getLastRestartTimestamp() != null 
            && now - instance.getLastRestartTimestamp() < 40_000) {
            return false;
        }
        
        return true;
    }
}
