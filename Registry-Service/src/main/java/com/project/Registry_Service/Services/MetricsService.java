package com.project.Registry_Service.Services;

import com.project.Registry_Service.Entity.InstanceState;
import com.project.Registry_Service.Entity.ServiceInstanceEntity;
import com.project.Registry_Service.Repository.ServiceInstanceRepository;
import com.project.Registry_Service.Repository.ServiceRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Service for recording and exposing Prometheus metrics.
 * Tracks service health, recovery operations, and system performance.
 */
@Slf4j
@Service
public class MetricsService {
    
    private final MeterRegistry meterRegistry;
    private final ServiceInstanceRepository instanceRepository;
    private final ServiceRepository serviceRepository;
    
    public MetricsService(MeterRegistry meterRegistry, 
                         ServiceInstanceRepository instanceRepository,
                         ServiceRepository serviceRepository) {
        this.meterRegistry = meterRegistry;
        this.instanceRepository = instanceRepository;
        this.serviceRepository = serviceRepository;
    }

    /**
     * Register gauge metrics that track current state counts
     */
    @PostConstruct
    public void registerMetrics() {
        log.info("Registering Prometheus gauge metrics");
        
        // Gauge for instances by state
        Gauge.builder("registry_instances_by_state", instanceRepository,
            repo -> repo.countByState(InstanceState.UP))
            .tag("state", "UP")
            .description("Number of service instances in UP state")
            .register(meterRegistry);
            
        Gauge.builder("registry_instances_by_state", instanceRepository,
            repo -> repo.countByState(InstanceState.SUSPECT))
            .tag("state", "SUSPECT")
            .description("Number of service instances in SUSPECT state")
            .register(meterRegistry);
            
        Gauge.builder("registry_instances_by_state", instanceRepository,
            repo -> repo.countByState(InstanceState.UNRESPONSIVE))
            .tag("state", "UNRESPONSIVE")
            .description("Number of service instances in UNRESPONSIVE state")
            .register(meterRegistry);
            
        Gauge.builder("registry_instances_by_state", instanceRepository,
            repo -> repo.countByState(InstanceState.QUARANTINED))
            .tag("state", "QUARANTINED")
            .description("Number of service instances in QUARANTINED state")
            .register(meterRegistry);
        
        // Gauge for total registered services
        Gauge.builder("registry_registered_services_total", serviceRepository,
            repo -> repo.count())
            .description("Total number of registered service types")
            .register(meterRegistry);
        
        // Gauge for total registered instances
        Gauge.builder("registry_registered_instances_total", instanceRepository,
            repo -> repo.count())
            .description("Total number of registered service instances")
            .register(meterRegistry);
        
        log.info("Prometheus gauge metrics registered successfully");
    }

    /**
     * Record heartbeat latency metric
     * 
     * @param instance the service instance
     * @param latencyMs latency in milliseconds since last heartbeat
     */
    public void recordHeartbeat(ServiceInstanceEntity instance, long latencyMs) {
        Timer.builder("registry_heartbeat_latency")
            .tag("service", instance.getService().getName())
            .description("Time since last heartbeat in seconds")
            .register(meterRegistry)
            .record(latencyMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Record service instance failure (transition to UNRESPONSIVE)
     * 
     * @param instance the service instance that failed
     */
    public void recordFailure(ServiceInstanceEntity instance) {
        Counter.builder("registry_failure_count_total")
            .tag("service", instance.getService().getName())
            .description("Total number of service instance failures")
            .register(meterRegistry)
            .increment();
        
        log.debug("Recorded failure for service: {}", instance.getService().getName());
    }

    /**
     * Record recovery operation result
     * 
     * @param instance the service instance
     * @param action the recovery action taken
     * @param success whether the recovery was successful
     */
    public void recordRecovery(ServiceInstanceEntity instance, RecoveryAction action, boolean success) {
        String metricName = success ? "registry_recovery_success_total" : "registry_recovery_failure_total";
        String actionName = action != null ? action.name() : "UNKNOWN";
        
        Counter.builder(metricName)
            .tag("service", instance.getService().getName())
            .tag("platform", instance.getService().getPlatform())
            .tag("action", actionName)
            .description(success ? "Total successful recovery operations" : "Total failed recovery operations")
            .register(meterRegistry)
            .increment();
        
        log.debug("Recorded {} recovery for service: {} (action: {})", 
            success ? "successful" : "failed", instance.getService().getName(), actionName);
    }

    /**
     * Record service instance restart
     * 
     * @param instance the service instance that was restarted
     */
    public void recordRestart(ServiceInstanceEntity instance) {
        Counter.builder("registry_restart_count_total")
            .tag("service", instance.getService().getName())
            .tag("platform", instance.getService().getPlatform())
            .description("Total number of service instance restarts")
            .register(meterRegistry)
            .increment();
        
        log.debug("Recorded restart for service: {}", instance.getService().getName());
    }

    /**
     * Record service instance transition to UP state
     * 
     * @param instance the service instance
     */
    public void recordServiceUp(ServiceInstanceEntity instance) {
        Counter.builder("registry_service_uptime_total")
            .tag("service", instance.getService().getName())
            .description("Total number of times service transitioned to UP state")
            .register(meterRegistry)
            .increment();
        
        log.debug("Recorded UP transition for service: {}", instance.getService().getName());
    }
}
