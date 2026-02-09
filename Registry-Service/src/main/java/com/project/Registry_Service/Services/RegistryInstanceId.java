package com.project.Registry_Service.Services;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class RegistryInstanceId
{
    private final String instanceId= UUID.randomUUID().toString();
    public String getInstanceId()
    {
        return instanceId;
    }
}
