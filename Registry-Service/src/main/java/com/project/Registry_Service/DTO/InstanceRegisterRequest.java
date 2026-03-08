package com.project.Registry_Service.DTO;

import lombok.Data;

@Data
public class InstanceRegisterRequest
{
    private String serviceName;
    private String host;
    private int port;
    private String baseUrl;
    private String healthPath;
    // Platform-specific fields (optional)
    private String containerName;  // For Docker
    private String ec2InstanceId;  // For EC2
    private String ec2Region;      // For EC2
}
