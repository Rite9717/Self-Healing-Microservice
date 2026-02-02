package com.project.ServiceA.Scheduler;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class HeartBeatSender
{
    private final RestTemplate restTemplate=new RestTemplate();
    private final String registryBase;
    private final String host;
    private final int port;

    public HeartBeatSender(
            @Value("${registry.base-url:http://localhost:8081/registry}") String registryBase,
            @Value("${service.host:sample-service-a}") String host,
            @Value("${server.port:9001}") int port) {
        this.registryBase = registryBase;
        this.host = host;
        this.port = port;
    }

    @Scheduled(initialDelay = 15000, fixedDelay = 5000)
    public void sendHeartBeat()
    {
        String url=registryBase+"/heartbeat?host="+host+"&port="+port;
        try
        {
            restTemplate.postForObject(url,null,Void.class);
            System.out.println("HeartBeat Sent");
        }
        catch(Exception e)
        {
            System.out.println("Registry not reachable");
        }
    }
}
