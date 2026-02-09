package com.project.Registry_Service.Services;

import com.project.Registry_Service.Entity.InstanceState;
import com.project.Registry_Service.Entity.ServiceInstanceEntity;
import com.project.Registry_Service.Repository.ServiceInstanceRepository;
import org.springframework.stereotype.Service;

@Service
public class RecoveryService
{
    private static final int MAX_RESTART_ATTEMPTS=3;
    private static final long QUARANTINE_DURATION_MS=1_200_000;
    private final DockerRecovery dockerRecovery;
    private final ServiceInstanceRepository instanceRepository;
    private final RedisLockService lockService;
    private final RegistryInstanceId registryInstanceId;
    private final RestartCounterService restartCounterService;

    RecoveryService(DockerRecovery dockerRecovery,ServiceInstanceRepository instanceRepository,RedisLockService lockService,RegistryInstanceId registryInstanceId,RestartCounterService restartCounterService)
    {
        this.dockerRecovery=dockerRecovery;
        this.instanceRepository = instanceRepository;
        this.lockService = lockService;
        this.registryInstanceId = registryInstanceId;
        this.restartCounterService = restartCounterService;
    }
    public void recover(ServiceInstanceEntity instance)
    {
        String lockKey="recovery:lock" + instance.getId();
        String ownerId=registryInstanceId.getInstanceId();
        if(!lockService.acquireLock(lockKey,ownerId,100))
        {
            return;
        }
        try
        {
            int attempts=restartCounterService.increment(instance.getId());
            if(attempts>MAX_RESTART_ATTEMPTS)
            {
                quarantine(instance);
                return;
            }
            String platform = instance.getService().getPlatform();
            switch (platform)
            {
                case  "docker" -> dockerRecovery.restart(instance);
                default -> {
                    quarantine(instance);
                    return;
                }
            }

            instance.setLastRestartTimestamp(System.currentTimeMillis());
            instanceRepository.save(instance);
        }
        finally {
            lockService.releaseLock(lockKey,ownerId);
        }
    }

    private void quarantine(ServiceInstanceEntity instance)
    {
        instance.setState(InstanceState.QUARANTINED);
        instance.setQuarantineUntilTimestamp(System.currentTimeMillis()+QUARANTINE_DURATION_MS);
        instanceRepository.save(instance);
    }

}
