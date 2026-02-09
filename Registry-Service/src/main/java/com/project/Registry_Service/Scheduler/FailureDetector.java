package com.project.Registry_Service.Scheduler;

import com.project.Registry_Service.Entity.InstanceState;
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
    private static final long HEARTBEAT_INTERVAL_MS=15_000;
    private static final int SUSPECT_THRESHOLD=2;
    private static final int UNRESPONSIVE_THRESHOLD=4;
    public FailureDetector(ServiceInstanceRepository serviceInstanceRepo,RecoveryService recovery)
    {
        this.serviceInstanceRepo=serviceInstanceRepo;
        this.recovery=recovery;
    }

    @Scheduled(fixedDelay = 10_000)
    public void detectFailures()
    {
        long now=System.currentTimeMillis();
        for(ServiceInstanceEntity instance: serviceInstanceRepo.findAll())
        {
            long silence = now - instance.getLastHeartBeat();
            switch (instance.getState())
            {
                case UP ->
                {
                    if (silence>HEARTBEAT_INTERVAL_MS)
                    {
                        instance.setMissedHeartBeats(1);
                        instance.setState(InstanceState.SUSPECT);
                    }
                }

                case SUSPECT -> {
                    if(silence<= HEARTBEAT_INTERVAL_MS)
                    {
                        instance.setMissedHeartBeats(0);
                        instance.setState(InstanceState.UP);
                    }
                    else
                    {
                        int missed = instance.getMissedHeartBeats()+1;
                        instance.setMissedHeartBeats(missed);
                        if(missed>= UNRESPONSIVE_THRESHOLD)
                        {
                            instance.setState(InstanceState.UNRESPONSIVE);
                        }
                    }
                }

                case UNRESPONSIVE -> {
                    if(silence<= HEARTBEAT_INTERVAL_MS)
                    {
                        instance.setMissedHeartBeats(0);
                        instance.setState(InstanceState.UP);
                    }
                    else if(canAttemptRecovery(instance,now))
                    {
                        recovery.recover(instance);
                    }
                }

                case QUARANTINED -> {
                    if(instance.getQuarantineUntilTimestamp()!=null && now >=instance.getQuarantineUntilTimestamp())
                    {
                        instance.setState(InstanceState.UNRESPONSIVE);
                    }
                }
            }
            serviceInstanceRepo.save(instance);
        }
    }

    private boolean canAttemptRecovery(ServiceInstanceEntity instance,long now)
    {
        if(instance.getQuarantineUntilTimestamp()!=null && now< instance.getQuarantineUntilTimestamp())
        {
            return false;
        }
        if(instance.getLastRestartTimestamp()!=null && now-instance.getLastRestartTimestamp()<40_000)
        {
            return false;
        }
        return true;
    }
}
