package com.project.Registry_Service.Entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "services")
public class ServiceEntity
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique= true, nullable = false)
    private String name;

    @Column(nullable = false)
    private String platform;

    private String version;
}