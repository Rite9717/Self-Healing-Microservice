package com.project.Registry_Service.Services;

import com.project.Registry_Service.Entity.RecoveryPolicyConfig;
import com.project.Registry_Service.Entity.ServiceEntity;
import com.project.Registry_Service.Repository.ServiceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RecoveryPolicyService {
    
    private final ServiceRepository serviceRepository;
    
    // Default policy values
    private static final RecoveryPolicyConfig DEFAULT_POLICY = new RecoveryPolicyConfig();
    
    static {
        DEFAULT_POLICY.setMaxRestartAttempts(3);
        DEFAULT_POLICY.setQuarantineDurationMs(1_200_000L); // 20 minutes
        DEFAULT_POLICY.setPreferredRecoveryActions("RESTART,START");
    }
    
    public RecoveryPolicyService(ServiceRepository serviceRepository) {
        this.serviceRepository = serviceRepository;
    }

    /**
     * Get the recovery policy for a specific service type.
     * Returns the service's configured policy or default policy if not configured.
     * 
     * @param serviceName the name of the service
     * @return the recovery policy configuration
     */
    public RecoveryPolicyConfig getPolicyForService(String serviceName) {
        return serviceRepository.findByName(serviceName)
                .map(ServiceEntity::getRecoveryPolicy)
                .map(policy -> policy != null ? policy : DEFAULT_POLICY)
                .orElseGet(() -> {
                    log.warn("Service {} not found, using default recovery policy", serviceName);
                    return DEFAULT_POLICY;
                });
    }

    /**
     * Get the default recovery policy
     * 
     * @return the default recovery policy configuration
     */
    public RecoveryPolicyConfig getDefaultPolicy() {
        return DEFAULT_POLICY;
    }
}
