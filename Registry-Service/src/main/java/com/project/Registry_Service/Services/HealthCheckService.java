package com.project.Registry_Service.Services;

import com.project.Registry_Service.Entity.ServiceInstanceEntity;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Service for performing HTTP health checks on service instances.
 * Uses circuit breakers to prevent cascading failures.
 */
@Slf4j
@Service
public class HealthCheckService {
    
    private final RestTemplate restTemplate;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    
    public HealthCheckService(CircuitBreakerRegistry circuitBreakerRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.restTemplate = new RestTemplate();
        
        // Configure timeouts
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(2000); // 2 seconds connect timeout
        factory.setConnectionRequestTimeout(3000); // 3 seconds read timeout
        this.restTemplate.setRequestFactory(factory);
    }

    /**
     * Perform health check on a service instance
     * 
     * @param instance the service instance to check
     * @return true if health check passes (HTTP 2xx), false otherwise
     */
    public boolean check(ServiceInstanceEntity instance) {
        if (instance.getHealthPath() == null || instance.getHealthPath().isEmpty()) {
            // No health check configured, rely on heartbeat only
            return false;
        }
        
        String healthUrl = buildHealthUrl(instance);
        
        // Get or create circuit breaker for this instance's health checks
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(
            "health-check-" + instance.getId()
        );
        
        try {
            return circuitBreaker.executeSupplier(() -> performHealthCheck(healthUrl));
        } catch (Exception e) {
            log.debug("Health check failed for instance {} at {}: {}", 
                instance.getId(), healthUrl, e.getMessage());
            return false;
        }
    }

    /**
     * Build the complete health check URL
     */
    private String buildHealthUrl(ServiceInstanceEntity instance) {
        String baseUrl = instance.getBaseUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "http://" + instance.getHost() + ":" + instance.getPort();
        }
        
        String healthPath = instance.getHealthPath();
        if (!healthPath.startsWith("/")) {
            healthPath = "/" + healthPath;
        }
        
        return baseUrl + healthPath;
    }

    /**
     * Perform the actual HTTP health check
     */
    private boolean performHealthCheck(String healthUrl) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                healthUrl,
                HttpMethod.GET,
                null,
                String.class
            );
            
            boolean isHealthy = response.getStatusCode().is2xxSuccessful();
            
            if (isHealthy) {
                log.trace("Health check passed for {}", healthUrl);
            } else {
                log.debug("Health check returned non-2xx status for {}: {}", 
                    healthUrl, response.getStatusCode());
            }
            
            return isHealthy;
            
        } catch (Exception e) {
            log.debug("Health check request failed for {}: {}", healthUrl, e.getMessage());
            throw new RuntimeException("Health check failed", e);
        }
    }
}
