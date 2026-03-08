package com.project.Registry_Service.Config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Validates required configuration at startup and logs active configuration.
 */
@Slf4j
@Configuration
public class StartupValidator {
    
    private final Environment environment;
    
    @Value("${spring.datasource.url}")
    private String datasourceUrl;
    
    @Value("${spring.redis.host}")
    private String redisHost;
    
    @Value("${registry.recovery.ec2.enabled:false}")
    private boolean ec2Enabled;
    
    public StartupValidator(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validateConfiguration() {
        log.info("=== Registry Service Startup Validation ===");
        
        // Log active profile
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length > 0) {
            log.info("Active profiles: {}", String.join(", ", activeProfiles));
        } else {
            log.info("Active profile: default");
        }
        
        // Validate required configuration
        validateRequired("spring.datasource.url", datasourceUrl);
        validateRequired("spring.redis.host", redisHost);
        
        // Log key configuration parameters
        log.info("Database URL: {}", maskPassword(datasourceUrl));
        log.info("Redis host: {}", redisHost);
        log.info("EC2 recovery enabled: {}", ec2Enabled);
        
        if (ec2Enabled) {
            log.info("AWS EC2 recovery is ENABLED - ensure AWS credentials are configured");
        }
        
        log.info("=== Startup Validation Complete ===");
    }

    private void validateRequired(String propertyName, String value) {
        if (value == null || value.trim().isEmpty()) {
            String message = String.format(
                "Required configuration property '%s' is missing or empty", 
                propertyName
            );
            log.error(message);
            throw new IllegalStateException(message);
        }
    }

    private String maskPassword(String url) {
        if (url == null) return null;
        return url.replaceAll("password=[^&;]*", "password=***");
    }
}
