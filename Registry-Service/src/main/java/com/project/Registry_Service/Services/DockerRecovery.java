package com.project.Registry_Service.Services;

import com.github.dockerjava.api.DockerClient;
import com.project.Registry_Service.Entity.ServiceInstanceEntity;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Service
public class DockerRecovery
{
    private final DockerClient dockerClient;
    public DockerRecovery(DockerClient dockerClient)
    {
        this.dockerClient=dockerClient;
    }

    public void restart(ServiceInstanceEntity instance)
    {
        String containerName = instance.getContainerName();
        if (containerName == null || containerName.isEmpty()) {
            System.out.println("Cannot restart - no container name for instance: " + instance.getHost() + ":" + instance.getPort());
            return;
        }
        
        try {
            System.out.println("Attempting to restart container: " + containerName);
            dockerClient.restartContainerCmd(containerName).exec();
            System.out.println("Successfully restarted container: " + containerName);
        } catch (Exception e) {
            System.err.println("Failed to restart container " + containerName + ": " + e.getClass().getName() + " - " + e.getMessage());
            e.printStackTrace();
        }
    }
}
