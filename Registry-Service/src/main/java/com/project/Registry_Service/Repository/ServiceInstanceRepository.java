package com.project.Registry_Service.Repository;

import com.project.Registry_Service.Entity.ServiceInstanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ServiceInstanceRepository extends JpaRepository<ServiceInstanceEntity,Long>
{
    Optional<ServiceInstanceEntity> findByHostAndPort(String host,int port);

    @Query("SELECT i FROM ServiceInstanceEntity i WHERE i.service.name= :name AND i.status= :status")
    List<ServiceInstanceEntity> findByServiceNameAndStatus(String name,String status);
}
