package com.project.Registry_Service.Services;

import com.project.Registry_Service.Entity.ServiceInstanceEntity;
import org.springframework.stereotype.Service;

@Service
public class RecoveryService
{
    private final DockerRecovery dockerRecovery;
    RecoveryService(DockerRecovery dockerRecovery)
    {
        this.dockerRecovery=dockerRecovery;
    }
    public void recover(ServiceInstanceEntity instance)
    {
        String platform=instance.getService().getPlatform();
        switch (platform)
        {
            case "docker" -> dockerRecovery.restart(instance);
            default -> System.out.println("No recovery strategy");
        }
    }
}
