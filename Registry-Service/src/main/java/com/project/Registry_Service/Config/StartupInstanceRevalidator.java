package com.project.Registry_Service.Config;

import com.project.Registry_Service.Entity.InstanceState;
import com.project.Registry_Service.Entity.ServiceInstanceEntity;
import com.project.Registry_Service.Repository.ServiceInstanceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Re-validates all existing service instances on startup.
 * Marks all instances as SUSPECT to trigger health re-validation.
 */
@Slf4j
@Component
public class StartupInstanceRevalidator {
    
    private final ServiceInstanceRepository instanceRepository;
    
    public StartupInstanceRevalidator(ServiceInstanceRepository instanceRepository) {
        this.instanceRepository = instanceRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void revalidateInstances() {
        log.info("=== Starting instance re-validation on startup ===");
        
        List<ServiceInstanceEntity> allInstances = instanceRepository.findAll();
        
        if (allInstances.isEmpty()) {
            log.info("No existing instances to re-validate");
            return;
        }
        
        int revalidatedCount = 0;
        
        for (ServiceInstanceEntity instance : allInstances) {
            // Skip already quarantined instances
            if (instance.getState() == InstanceState.QUARANTINED) {
                log.debug("Skipping quarantined instance: {} ({}:{})", 
                    instance.getId(), instance.getHost(), instance.getPort());
                continue;
            }
            
            // Mark as SUSPECT to trigger re-validation
            instance.setState(InstanceState.SUSPECT);
            instance.setMissedHeartBeats(0);
            instanceRepository.save(instance);
            
            revalidatedCount++;
            log.debug("Marked instance {} for re-validation: {}:{}", 
                instance.getId(), instance.getHost(), instance.getPort());
        }
        
        log.info("Marked {} instances for re-validation (total: {}, quarantined: {})", 
            revalidatedCount, allInstances.size(), allInstances.size() - revalidatedCount);
        log.info("=== Instance re-validation complete ===");
    }
}
