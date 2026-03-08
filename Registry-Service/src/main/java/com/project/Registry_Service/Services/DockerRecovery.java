package com.project.Registry_Service.Services;

import com.github.dockerjava.api.DockerClient;
import com.project.Registry_Service.Entity.ServiceInstanceEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
public class DockerRecovery implements RecoveryStrategy
{
    private final DockerClient dockerClient;
    
    public DockerRecovery(DockerClient dockerClient)
    {
        this.dockerClient = dockerClient;
    }

    @Override
    public String getPlatform() {
        return "docker";
    }

    @Override
    public boolean canRecover(ServiceInstanceEntity instance) {
        return "docker".equals(instance.getService().getPlatform()) 
            && instance.getContainerName() != null 
            && !instance.getContainerName().isEmpty();
    }

    @Override
    public RecoveryResult recover(ServiceInstanceEntity instance) {
        String containerName = instance.getContainerName();
        
        if (containerName == null || containerName.isEmpty()) {
            String message = "Cannot restart - no container name for instance: " + 
                instance.getHost() + ":" + instance.getPort();
            log.error(message);
            return RecoveryResult.failure(message, null);
        }
        
        try {
            log.info("Attempting to restart Docker container: {}", containerName);
            dockerClient.restartContainerCmd(containerName).exec();
            log.info("Successfully restarted Docker container: {}", containerName);
            return RecoveryResult.success(RecoveryAction.RESTART, 
                "Docker container restarted successfully");
        } catch (Exception e) {
            String message = "Failed to restart container " + containerName + ": " + e.getMessage();
            log.error(message, e);
            return RecoveryResult.failure(RecoveryAction.RESTART, message, e);
        }
    }

    @Override
    public List<RecoveryAction> getAvailableActions() {
        return Arrays.asList(RecoveryAction.RESTART, RecoveryAction.START, RecoveryAction.STOP);
    }

    /**
     * Legacy method for backward compatibility
     * @deprecated Use recover() method instead
     */
    @Deprecated
    public void restart(ServiceInstanceEntity instance) {
        recover(instance);
    }
}
