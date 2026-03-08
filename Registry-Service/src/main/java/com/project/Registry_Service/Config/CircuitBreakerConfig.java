package com.project.Registry_Service.Config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class CircuitBreakerConfig {
    
    /**
     * Configure circuit breaker registry with settings for recovery operations.
     * 
     * Configuration:
     * - Failure rate threshold: 50% (opens after 50% of calls fail)
     * - Wait duration in open state: 20 minutes (quarantine duration)
     * - Sliding window size: 10 calls
     * - Minimum number of calls: 3 (before calculating failure rate)
     * - Permitted calls in half-open: 1 (test with single call)
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        io.github.resilience4j.circuitbreaker.CircuitBreakerConfig config = 
            io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMinutes(20))
                .slidingWindowSize(10)
                .minimumNumberOfCalls(3)
                .permittedNumberOfCallsInHalfOpenState(1)
                .build();
            
        return CircuitBreakerRegistry.of(config);
    }
}
