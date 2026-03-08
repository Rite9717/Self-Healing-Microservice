package com.project.Registry_Service.Config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;

@Slf4j
@Configuration
public class AwsConfig {

    /**
     * Creates EC2 client bean with credential provider chain support.
     * Only created when registry.recovery.ec2.enabled=true
     * 
     * Credential provider chain order:
     * 1. Environment variables (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY)
     * 2. System properties (aws.accessKeyId, aws.secretKey)
     * 3. IAM instance profile (when running on EC2)
     * 4. AWS credentials file (~/.aws/credentials)
     */
    @Bean
    @ConditionalOnProperty(name = "registry.recovery.ec2.enabled", havingValue = "true")
    public Ec2Client ec2Client() {
        log.info("Initializing AWS EC2 client with default credentials provider chain");
        
        return Ec2Client.builder()
                .credentialsProvider(DefaultCredentialsProvider.create())
                .region(Region.US_EAST_1) // Default region, can be overridden per instance
                .build();
    }
}
