package com.project.ServiceA.Registration;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
public class ServiceRegistrar implements CommandLineRunner
{
    private final RestTemplate restTemplate=new RestTemplate();
    private final String registryBase;
    private final String serviceHost;
    private final int servicePort;
    
    public ServiceRegistrar(
            @org.springframework.beans.factory.annotation.Value("${registry.base-url:http://localhost:8081/registry}") String registryBase,
            @org.springframework.beans.factory.annotation.Value("${service.host:sample-service-a}") String serviceHost,
            @org.springframework.beans.factory.annotation.Value("${server.port:9001}") int servicePort) {
        this.registryBase = registryBase;
        this.serviceHost = serviceHost;
        this.servicePort = servicePort;
    }
    @Override
    public void run(String... args) throws Exception
    {
        while (true) {
            try {
                register();
                break;   // success → exit loop
            } catch (Exception e) {
                System.out.println("Registry not ready yet, retrying in 5 seconds...");
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ignored) {}
            }
        }
    }
    private void register()
    {
        Map<String, Object> service = new HashMap<>();
        service.put("name", "sample-service-a");
        service.put("platform", "docker");
        service.put("version", "1.0");

        restTemplate.postForObject(registryBase + "/service", service, Object.class);

        // 2. Register instance
        Map<String, Object> instance = new HashMap<>();
        instance.put("serviceName", "sample-service-a");
        instance.put("host", serviceHost);
        instance.put("port", servicePort);
        instance.put("baseUrl", "http://" + serviceHost + ":" + servicePort);
        instance.put("healthPath", "/actuator/health");
        
        // Get container name from environment or hostname
        String containerName = System.getenv("HOSTNAME");
        if (containerName != null && !containerName.isEmpty()) {
            instance.put("containerName", containerName);
        }

        restTemplate.postForObject(registryBase + "/instance", instance, Object.class);

        System.out.println("Service & Instance registered successfully");
    }
}
