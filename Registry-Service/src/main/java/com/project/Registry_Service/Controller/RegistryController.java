package com.project.Registry_Service.Controller;

import com.project.Registry_Service.DTO.InstanceRegisterRequest;
import com.project.Registry_Service.Entity.ServiceEntity;
import com.project.Registry_Service.Entity.ServiceInstanceEntity;
import com.project.Registry_Service.Repository.ServiceInstanceRepository;
import com.project.Registry_Service.Repository.ServiceRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/registry")
public class RegistryController
{
    private final ServiceRepository serviceRepo;
    private final ServiceInstanceRepository serviceInstanceRepo;
    RegistryController(ServiceRepository serviceRepo,ServiceInstanceRepository serviceInstanceRepo)
    {
        this.serviceRepo=serviceRepo;
        this.serviceInstanceRepo=serviceInstanceRepo;
    }

    @PostMapping("/service")
    public ServiceEntity registerService(@RequestBody ServiceEntity service)
    {
        return serviceRepo.findByName(service.getName())
                .orElseGet(() -> serviceRepo.save(service));
    }

    @PostMapping("/instance")
    public ServiceInstanceEntity registerInstance(@RequestBody InstanceRegisterRequest request)
    {
        ServiceEntity service=serviceRepo.findByName(request.getServiceName())
                .orElseThrow(() -> new RuntimeException("Service not registered: "+request.getServiceName()));
        ServiceInstanceEntity instance=serviceInstanceRepo.findByHostAndPort(request.getHost(),request.getPort())
                .orElse(new ServiceInstanceEntity());
        instance.setService(service);
        instance.setHost(request.getHost());
        instance.setPort(request.getPort());
        instance.setBaseUrl(request.getBaseUrl());
        instance.setHealthPath(request.getHealthPath());
        instance.setContainerName(request.getContainerName());
        instance.setStatus("UP");
        instance.setLastHeartbeat(System.currentTimeMillis());
        instance.setResponseTime(0L);

        return serviceInstanceRepo.save(instance);
    }

    @GetMapping("/services")
    public List<ServiceEntity> getServices()
    {
        return serviceRepo.findAll();
    }

    @GetMapping("/instances")
    public List<ServiceInstanceEntity> getInstances()
    {
        return serviceInstanceRepo.findAll();
    }

    @GetMapping("/instances/all")
    public List<ServiceInstanceEntity> getAllInstances() {
        return serviceInstanceRepo.findAll();
    }

    @GetMapping("/instances/{serviceName}")
    public List<ServiceInstanceEntity> getHealthyInstances(@PathVariable String serviceName)
    {
        return serviceInstanceRepo.findByServiceNameAndStatus(serviceName,"UP");
    }

    @PostMapping("/heartbeat")
    public void heartbeat(@RequestParam String host,@RequestParam int port)
    {
        ServiceInstanceEntity instance=serviceInstanceRepo.findByHostAndPort(host, port)
                .orElse(null);
        if(instance==null)
        {
            System.out.println("Heartbeat ignored (instance not registered yet): "+ host + ":" + port);
            return;
        }
        instance.setLastHeartbeat(System.currentTimeMillis());
        instance.setStatus("UP");
        serviceInstanceRepo.save(instance);
    }

}
