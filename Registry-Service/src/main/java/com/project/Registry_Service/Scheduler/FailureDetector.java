package com.project.Registry_Service.Scheduler;

import com.project.Registry_Service.Entity.ServiceInstanceEntity;
import com.project.Registry_Service.Repository.ServiceInstanceRepository;
import com.project.Registry_Service.Services.RecoveryService;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@EnableScheduling
@Component
public class FailureDetector
{
    private final ServiceInstanceRepository serviceInstanceRepo;
    private final RecoveryService recovery;
    public FailureDetector(ServiceInstanceRepository serviceInstanceRepo,RecoveryService recovery)
    {
        this.serviceInstanceRepo=serviceInstanceRepo;
        this.recovery=recovery;
    }

    @Scheduled(fixedDelay = 10000)
    public void detectFailures()
    {
        long now=System.currentTimeMillis();
        List<ServiceInstanceEntity> instances=serviceInstanceRepo.findAll();
        for(ServiceInstanceEntity instance: instances)
        {
            if("UP".equals(instance.getStatus()) && now-instance.getLastHeartbeat()> 15000)
            {
                instance.setStatus("DOWN");
                serviceInstanceRepo.save(instance);
                recovery.recover(instance);
            }
        }
    }
}
