package com.project.Registry_Service.Services;

import com.project.Registry_Service.Entity.InstanceState;
import com.project.Registry_Service.Entity.RecoveryPolicyConfig;
import com.project.Registry_Service.Entity.ServiceInstanceEntity;
import com.project.Registry_Service.Repository.ServiceInstanceRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class RecoveryService
{
    private final List<RecoveryStrategy> strategies;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final ServiceInstanceRepository instanceRepository;
    private final RedisLockService lockService;
    private final RegistryInstanceId registryInstanceId;
    private final RestartCounterService restartCounterService;
    private final RecoveryEventPublisher eventPublisher;
    private final RecoveryPolicyService policyService;
    private final MetricsService metricsService;

    RecoveryService(
            List<RecoveryStrategy> strategies,
            CircuitBreakerRegistry circuitBreakerRegistry,
            ServiceInstanceRepository instanceRepository,
            RedisLockService lockService,
            RegistryInstanceId registryInstanceId,
            RestartCounterService restartCounterService,
            RecoveryEventPublisher eventPublisher,
            RecoveryPolicyService policyService,
            MetricsService metricsService)
    {
        this.strategies = strategies;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.instanceRepository = instanceRepository;
        this.lockService = lockService;
        this.registryInstanceId = registryInstanceId;
        this.restartCounterService = restartCounterService;
        this.eventPublisher = eventPublisher;
        this.policyService = policyService;
        this.metricsService = metricsService;
        
        log.info("RecoveryService initialized with {} recovery strategies", strategies.size());
        strategies.forEach(s -> log.info("  - {} strategy", s.getPlatform()));
    }
    
    public void recover(ServiceInstanceEntity instance)
    {
        String lockKey = "recovery:lock:" + instance.getId();
        String ownerId = registryInstanceId.getInstanceId();
        
        if (!lockService.acquireLock(lockKey, ownerId, 2000)) {
            log.debug("Could not acquire lock for instance {}, another registry is handling recovery", 
                instance.getId());
            return;
        }
        
        try {
            // Get circuit breaker for this instance
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(
                "recovery-" + instance.getId()
            );
            
            // Check circuit breaker state
            if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
                log.warn("Circuit breaker is OPEN for instance {}, quarantining", instance.getId());
                RecoveryPolicyConfig policy = policyService.getPolicyForService(
                    instance.getService().getName()
                );
                quarantine(instance, policy.getQuarantineDurationMs());
                eventPublisher.publishEvent(new RecoveryEvent(
                    instance, null, false, "Circuit breaker is OPEN"
                ));
                return;
            }
            
            // Get recovery policy for this service type
            RecoveryPolicyConfig policy = policyService.getPolicyForService(
                instance.getService().getName()
            );
            
            // Check restart attempts
            int attempts = restartCounterService.getCount(instance.getId());
            if (attempts >= policy.getMaxRestartAttempts()) {
                log.warn("Instance {} exceeded max restart attempts ({}), quarantining", 
                    instance.getId(), policy.getMaxRestartAttempts());
                quarantine(instance, policy.getQuarantineDurationMs());
                eventPublisher.publishEvent(new RecoveryEvent(
                    instance, null, false, 
                    "Exceeded max restart attempts (" + policy.getMaxRestartAttempts() + ")"
                ));
                return;
            }
            
            // Select recovery strategy
            RecoveryStrategy strategy = selectStrategy(instance);
            if (strategy == null) {
                log.error("No recovery strategy available for platform: {}", 
                    instance.getService().getPlatform());
                eventPublisher.publishEvent(new RecoveryEvent(
                    instance, null, false, "No recovery strategy available"
                ));
                quarantine(instance, policy.getQuarantineDurationMs());
                return;
            }
            
            log.info("Attempting recovery for instance {} using {} strategy (circuit breaker: {})", 
                instance.getId(), strategy.getPlatform(), circuitBreaker.getState());
            
            // Execute recovery through circuit breaker
            RecoveryResult result = circuitBreaker.executeSupplier(() -> strategy.recover(instance));
            
            if (result.success()) {
                log.info("Recovery successful for instance {}: {}", instance.getId(), result.message());
                instance.setLastRestartTimestamp(System.currentTimeMillis());
                restartCounterService.increment(instance.getId());
                instanceRepository.save(instance);
                
                // Record metrics
                metricsService.recordRecovery(instance, result.actionTaken(), true);
                metricsService.recordRestart(instance);
                
                eventPublisher.publishEvent(new RecoveryEvent(
                    instance, result.actionTaken(), true, result.message()
                ));
            } else {
                log.error("Recovery failed for instance {}: {}", instance.getId(), result.message());
                restartCounterService.increment(instance.getId());
                
                // Record failure metric
                metricsService.recordRecovery(instance, result.actionTaken(), false);
                
                eventPublisher.publishEvent(new RecoveryEvent(
                    instance, result.actionTaken(), false, result.message()
                ));
                
                // Throw exception to trigger circuit breaker
                throw new RuntimeException("Recovery failed: " + result.message());
            }
            
        } catch (Exception e) {
            log.error("Error during recovery for instance " + instance.getId(), e);
            eventPublisher.publishEvent(new RecoveryEvent(
                instance, null, false, "Recovery exception: " + e.getMessage()
            ));
            
            // Re-throw to ensure circuit breaker records the failure
            throw new RuntimeException(e);
        } finally {
            lockService.releaseLock(lockKey, ownerId);
        }
    }

    /**
     * Select the appropriate recovery strategy for the instance
     */
    private RecoveryStrategy selectStrategy(ServiceInstanceEntity instance) {
        String platform = instance.getService().getPlatform();
        
        return strategies.stream()
            .filter(s -> s.getPlatform().equals(platform))
            .filter(s -> s.canRecover(instance))
            .findFirst()
            .orElse(null);
    }

    /**
     * Quarantine an instance for the specified duration
     */
    private void quarantine(ServiceInstanceEntity instance, Long durationMs) {
        instance.setState(InstanceState.QUARANTINED);
        instance.setQuarantineUntilTimestamp(System.currentTimeMillis() + durationMs);
        instanceRepository.save(instance);
        log.info("Instance {} quarantined until {}", instance.getId(), 
            instance.getQuarantineUntilTimestamp());
    }
}
