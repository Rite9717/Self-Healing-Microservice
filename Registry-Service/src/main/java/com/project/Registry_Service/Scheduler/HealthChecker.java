package com.project.Registry_Service.Scheduler;

import com.project.Registry_Service.Entity.ServiceInstanceEntity;
import com.project.Registry_Service.Repository.ServiceInstanceRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class HealthChecker
{
    private final ServiceInstanceRepository serviceInstanceRepo;
    private final RestTemplate restTemplate=new RestTemplate();
    HealthChecker(ServiceInstanceRepository serviceInstanceRepo)
    {
        this.serviceInstanceRepo=serviceInstanceRepo;
    }
    public void healthcheck()
    {
        for(ServiceInstanceEntity i : serviceInstanceRepo.findAll()) {
            try {
                long start = System.currentTimeMillis();
                restTemplate.getForObject(i.getBaseUrl() + i.getHealthPath(), String.class);
                i.setResponseTime(System.currentTimeMillis() - start);
                i.setStatus("UP");
            } catch (Exception e) {
                i.setStatus("DOWN");
            }
            serviceInstanceRepo.save(i);
        }
    }
}
