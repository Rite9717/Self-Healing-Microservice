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
    private String containerName;
}
