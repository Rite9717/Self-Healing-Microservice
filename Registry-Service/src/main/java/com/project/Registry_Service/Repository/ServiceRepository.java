package com.project.Registry_Service.Repository;

import com.project.Registry_Service.Entity.ServiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ServiceRepository extends JpaRepository<ServiceEntity,Long>
{
    Optional<ServiceEntity> findByName(String name);
}
