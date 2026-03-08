package com.project.Registry_Service.Entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.sql.Timestamp;

@Data
@Entity
@Table(name = "service_instances",uniqueConstraints = {@UniqueConstraint(columnNames = {"host","port"})})
public class ServiceInstanceEntity
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceEntity service;

    @Column(nullable = false)
    private String host;

    @Column(nullable = false)
    private int port;

    private String baseUrl;

    private String healthPath;

    // Docker-specific fields
    private String containerName;

    // EC2-specific fields
    private String ec2InstanceId;
    private String ec2Region;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InstanceState state=InstanceState.UP;

    @Column(nullable = false)
    private  int missedHeartBeats=0;

    @Column(nullable = false)
    private Long lastHeartBeat;

    private Long lastRestartTimestamp;

    private Long quarantineUntilTimestamp;

    private Long responseTime;

    // Timestamp tracking for grace period and auditing
    @Column(nullable = false, updatable = false)
    @CreationTimestamp
    private Timestamp createdAt;

    @UpdateTimestamp
    private Timestamp updatedAt;
}
