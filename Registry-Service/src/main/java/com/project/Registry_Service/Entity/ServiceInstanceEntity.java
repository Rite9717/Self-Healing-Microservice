package com.project.Registry_Service.Entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "service_instances")
public class ServiceInstanceEntity
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceEntity service;

    private String host;

    private int port;

    private String baseUrl;

    private String healthPath;

    private String status;

    private Long responseTime;

    private Long lastHeartbeat;

    private String containerName;
}
