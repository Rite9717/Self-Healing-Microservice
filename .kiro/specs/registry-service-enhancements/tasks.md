# Implementation Plan: Registry Service Enhancements

## Overview

This implementation plan enhances the Sentinel Self-Healing Microservice Registry with AWS EC2 recovery, Prometheus metrics, multi-instance support, enhanced failure detection, and pluggable recovery strategies. The implementation follows an incremental approach, building core abstractions first, then adding platform-specific implementations, and finally integrating observability and advanced features.

## Tasks

- [x] 1. Database schema updates and entity enhancements
  - Add new columns to service_instances table: ec2InstanceId, ec2Region, createdAt, updatedAt
  - Add new columns to services table: heartbeatThresholdMs, loadBalancingStrategy, serviceVersion
  - Create embedded RecoveryPolicyConfig for services table
  - Update ServiceEntity with new fields and RecoveryPolicyConfig
  - Update ServiceInstanceEntity with EC2 fields and timestamps
  - Update InstanceRegisterRequest DTO with optional EC2 fields
  - _Requirements: 1.7, 1.8, 3.4, 4.1, 5.11_

- [ ] 2. Implement Recovery Strategy abstraction
  - [x] 2.1 Create RecoveryStrategy interface
    - Define getPlatform(), canRecover(), recover(), getAvailableActions() methods
    - Create RecoveryAction enum (RESTART, START, STOP, RECREATE)
    - Create RecoveryResult record with success, actionTaken, message, error fields
    - _Requirements: 5.1, 5.2_
  
  - [x] 2.2 Refactor existing DockerRecovery to implement RecoveryStrategy
    - Implement RecoveryStrategy interface in DockerRecovery
    - Return "docker" from getPlatform()
    - Implement canRecover() to check for containerName
    - Update recover() to return RecoveryResult
    - _Requirements: 1.6, 5.2_
  
  - [ ]* 2.3 Write property test for Docker recovery backward compatibility
    - **Property 2: Docker Recovery Backward Compatibility**
    - **Validates: Requirements 1.6**

- [ ] 3. Implement EC2 Recovery Strategy
  - [x] 3.1 Add AWS SDK dependencies to pom.xml
    - Add software.amazon.awssdk:ec2 dependency version 2.20.0
    - Add AWS SDK BOM for dependency management
    - _Requirements: 1.1_
  
  - [x] 3.2 Create AWS EC2 client configuration
    - Create AwsConfig class with @Configuration
    - Create Ec2Client bean with credential provider chain support
    - Support IAM instance profile and environment variable credentials
    - Add conditional bean creation based on registry.recovery.ec2.enabled property
    - _Requirements: 7.3, 7.7_
  
  - [x] 3.3 Implement EC2RecoveryStrategy
    - Create EC2RecoveryStrategy class implementing RecoveryStrategy
    - Implement getPlatform() returning "ec2"
    - Implement canRecover() checking for ec2InstanceId
    - Implement determineAction() method mapping instance state to recovery action
    - Implement recover() with AWS SDK calls for reboot, start, stop
    - Add retry logic for transient errors (3 attempts with exponential backoff)
    - Handle non-retryable errors without retries
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 9.1, 9.2_
  
  - [ ]* 3.4 Write property test for EC2 recovery action selection
    - **Property 1: EC2 Recovery Action Selection**
    - **Validates: Requirements 1.2, 1.3, 1.4**
  
  - [ ]* 3.5 Write property test for EC2 retry on transient errors
    - **Property 28: EC2 Recovery Retry on Transient Errors**
    - **Validates: Requirements 9.1**
  
  - [ ]* 3.6 Write property test for non-retryable error quarantine
    - **Property 29: Non-Retryable Error Quarantine**
    - **Validates: Requirements 9.2**

- [ ] 4. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 5. Implement enhanced Recovery Service with strategy selection
  - [x] 5.1 Create RecoveryPolicyService
    - Create service to load and manage recovery policies per service type
    - Implement getPolicyForService() method
    - Support default policy values
    - _Requirements: 5.11, 5.12_
  
  - [x] 5.2 Create RecoveryEvent and RecoveryEventPublisher
    - Create RecoveryEvent record with instance details, action, outcome, timestamp
    - Create RecoveryEventPublisher component
    - Implement publishEvent() method using Spring ApplicationEventPublisher
    - _Requirements: 5.9_
  
  - [x] 5.3 Refactor RecoveryService with strategy pattern
    - Inject List<RecoveryStrategy> strategies via constructor
    - Implement selectStrategy() method to find strategy by platform
    - Update recover() to use selected strategy
    - Implement recovery action ordering based on Recovery Policy
    - Publish RecoveryEvent on success and failure
    - _Requirements: 5.2, 5.9, 5.12_
  
  - [ ]* 5.4 Write property test for recovery strategy selection
    - **Property 18: Recovery Strategy Selection**
    - **Validates: Requirements 5.2**
  
  - [ ]* 5.5 Write property test for recovery action ordering
    - **Property 24: Recovery Action Ordering**
    - **Validates: Requirements 5.12**
  
  - [ ]* 5.6 Write property test for recovery event publication
    - **Property 23: Recovery Event Publication**
    - **Validates: Requirements 5.9**
  
  - [ ]* 5.7 Write property test for recovery strategy exception handling
    - **Property 32: Recovery Strategy Exception Handling**
    - **Validates: Requirements 9.5**

- [ ] 6. Implement Circuit Breaker integration
  - [x] 6.1 Add Resilience4j dependencies to pom.xml
    - Add io.github.resilience4j:resilience4j-spring-boot3 dependency version 2.1.0
    - _Requirements: 5.4_
  
  - [x] 6.2 Create CircuitBreakerConfig
    - Create configuration class with CircuitBreakerRegistry bean
    - Configure failure rate threshold (50%), wait duration (20 min), sliding window (10)
    - Configure minimum calls (3) and half-open calls (1)
    - _Requirements: 5.4, 5.6_
  
  - [x] 6.3 Integrate circuit breaker in RecoveryService
    - Create circuit breaker per instance: "recovery-" + instance.getId()
    - Check circuit breaker state before recovery
    - Execute recovery through circuit breaker
    - Handle open state by quarantining instance
    - _Requirements: 5.4, 5.5, 5.6, 5.7, 5.8_
  
  - [ ]* 6.4 Write property test for circuit breaker opening
    - **Property 19: Circuit Breaker Opening**
    - **Validates: Requirements 5.4, 5.5**
  
  - [ ]* 6.5 Write property test for circuit breaker half-open transition
    - **Property 20: Circuit Breaker Half-Open Transition**
    - **Validates: Requirements 5.6**
  
  - [ ]* 6.6 Write property test for circuit breaker closure on success
    - **Property 21: Circuit Breaker Closure on Success**
    - **Validates: Requirements 5.7**
  
  - [ ]* 6.7 Write property test for circuit breaker reopening on failure
    - **Property 22: Circuit Breaker Reopening on Failure**
    - **Validates: Requirements 5.8**

- [ ] 7. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 8. Implement Prometheus metrics integration
  - [x] 8.1 Add Micrometer Prometheus dependency to pom.xml
    - Add io.micrometer:micrometer-registry-prometheus dependency
    - Verify spring-boot-starter-actuator is present
    - _Requirements: 2.1_
  
  - [x] 8.2 Configure Actuator endpoints in application.yml
    - Expose prometheus, metrics, health, info endpoints
    - Configure metrics tags for application and environment
    - _Requirements: 2.2_
  
  - [x] 8.3 Create MetricsService
    - Inject MeterRegistry and ServiceInstanceRepository
    - Implement registerMetrics() with @PostConstruct for gauge metrics
    - Create gauges for registry_instances_by_state (UP, SUSPECT, UNRESPONSIVE, QUARANTINED)
    - Create gauges for registry_registered_services_total and registry_registered_instances_total
    - Implement recordHeartbeat() for heartbeat latency timer
    - Implement recordFailure() for failure counter
    - Implement recordRecovery() for recovery success/failure counters
    - Implement recordRestart() for restart counter
    - _Requirements: 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 2.9, 2.10_
  
  - [x] 8.4 Integrate MetricsService in RegistryController
    - Inject MetricsService
    - Call recordHeartbeat() in heartbeat endpoint with latency calculation
    - _Requirements: 2.6_
  
  - [x] 8.5 Integrate MetricsService in FailureDetector
    - Inject MetricsService
    - Call recordFailure() when transitioning to UNRESPONSIVE
    - _Requirements: 2.5_
  
  - [x] 8.6 Integrate MetricsService in RecoveryService
    - Inject MetricsService
    - Call recordRecovery() after recovery attempts
    - Call recordRestart() on successful recovery
    - _Requirements: 2.4, 2.7_
  
  - [ ]* 8.7 Write property test for metric recording on state transitions
    - **Property 3: Metric Recording on State Transitions**
    - **Validates: Requirements 2.3, 2.4, 2.5, 2.7**
  
  - [ ]* 8.8 Write property test for heartbeat latency metric recording
    - **Property 4: Heartbeat Latency Metric Recording**
    - **Validates: Requirements 2.6**
  
  - [ ]* 8.9 Write property test for instance state gauge accuracy
    - **Property 5: Instance State Gauge Accuracy**
    - **Validates: Requirements 2.8**
  
  - [ ]* 8.10 Write property test for service and instance count gauge accuracy
    - **Property 6: Service and Instance Count Gauge Accuracy**
    - **Validates: Requirements 2.9, 2.10**

- [ ] 9. Implement enhanced failure detection
  - [x] 9.1 Create HealthCheckService
    - Create service with RestTemplate configured with timeouts (connect: 2s, read: 3s)
    - Inject CircuitBreakerRegistry
    - Implement check() method performing HTTP GET to healthPath
    - Wrap health check in circuit breaker per instance
    - Return true for 2xx responses, false otherwise
    - Handle timeouts and exceptions gracefully
    - _Requirements: 4.4, 4.5, 4.6, 9.6_
  
  - [x] 9.2 Enhance FailureDetector with grace period and custom thresholds
    - Add isInGracePeriod() method checking createdAt timestamp
    - Add getHeartbeatThreshold() method using service's custom threshold or default
    - Update detectFailures() to skip instances in grace period
    - Update state transition logic to use custom thresholds
    - _Requirements: 4.2, 4.3_
  
  - [x] 9.3 Integrate health checks in FailureDetector
    - Inject HealthCheckService
    - Add performHealthCheck() method
    - Update state transition logic to consider health check results
    - If health check passes, keep instance UP regardless of heartbeat
    - If both heartbeat and health check fail, transition to SUSPECT
    - Increment missed health check counter on failures
    - _Requirements: 4.4, 4.5, 4.6, 4.7_
  
  - [ ]* 9.4 Write property test for custom heartbeat threshold application
    - **Property 12: Custom Heartbeat Threshold Application**
    - **Validates: Requirements 4.2**
  
  - [ ]* 9.5 Write property test for grace period protection
    - **Property 13: Grace Period Protection**
    - **Validates: Requirements 4.3**
  
  - [ ]* 9.6 Write property test for health check execution
    - **Property 14: Health Check Execution**
    - **Validates: Requirements 4.4**
  
  - [ ]* 9.7 Write property test for health check override
    - **Property 15: Health Check Override**
    - **Validates: Requirements 4.5**
  
  - [ ]* 9.8 Write property test for failed health check counter
    - **Property 16: Failed Health Check Counter**
    - **Validates: Requirements 4.6**
  
  - [ ]* 9.9 Write property test for combined failure detection
    - **Property 17: Combined Failure Detection**
    - **Validates: Requirements 4.7**
  
  - [ ]* 9.10 Write property test for health check timeout handling
    - **Property 33: Health Check Timeout Handling**
    - **Validates: Requirements 9.6**

- [ ] 10. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 11. Implement multi-instance support enhancements
  - [x] 11.1 Add repository methods for multi-instance queries
    - Add countByState() method to ServiceInstanceRepository
    - Add findByServiceNameAndState() method (already exists, verify)
    - Add countByService() method to ServiceInstanceRepository
    - _Requirements: 3.1, 3.2_
  
  - [x] 11.2 Update RegistryController for multi-instance support
    - Verify /registry/instances/{serviceName} endpoint returns healthy instances
    - Add validation for unique host-port combinations in registerInstance()
    - Update registerInstance() to handle platform field with "docker" default
    - _Requirements: 3.2, 3.5, 3.8, 8.6_
  
  - [ ]* 11.3 Write property test for independent instance tracking
    - **Property 7: Independent Instance Tracking**
    - **Validates: Requirements 3.1**
  
  - [ ]* 11.4 Write property test for healthy instance query correctness
    - **Property 8: Healthy Instance Query Correctness**
    - **Validates: Requirements 3.2**
  
  - [ ]* 11.5 Write property test for independent service type configuration
    - **Property 9: Independent Service Type Configuration**
    - **Validates: Requirements 3.3**
  
  - [ ]* 11.6 Write property test for independent health evaluation
    - **Property 10: Independent Health Evaluation**
    - **Validates: Requirements 3.6**
  
  - [ ]* 11.7 Write property test for unique host-port validation
    - **Property 11: Unique Host-Port Validation**
    - **Validates: Requirements 3.8**
  
  - [ ]* 11.8 Write property test for platform default value
    - **Property 27: Platform Default Value**
    - **Validates: Requirements 8.6**

- [ ] 12. Implement notification system
  - [x] 12.1 Create RecoveryNotificationService
    - Create service with configurable notification type (log, webhook, sns)
    - Implement notify() method with switch on notification type
    - Implement logEvent() for log-based notifications
    - Implement sendWebhook() for webhook notifications (HTTP POST)
    - Implement sendSNS() stub for future SNS integration
    - _Requirements: 5.10_
  
  - [x] 12.2 Integrate notifications in RecoveryEventPublisher
    - Inject RecoveryNotificationService
    - Call notify() in publishEvent() method
    - _Requirements: 5.10_
  
  - [ ]* 12.3 Write unit tests for notification service
    - Test log notification format
    - Test webhook notification with mock HTTP client
    - Test notification type selection
    - _Requirements: 5.10_

- [ ] 13. Implement error handling and resilience
  - [x] 13.1 Enhance RedisLockService with fallback
    - Add try-catch around Redis operations
    - Implement in-memory ConcurrentHashMap fallback
    - Log warning when falling back to in-memory locking
    - _Requirements: 9.4_
  
  - [x] 13.2 Add startup validation in RegistryServiceApplication
    - Create @Component with @PostConstruct method
    - Validate required configuration properties
    - Fail fast with descriptive error messages if missing
    - Log active configuration profile and key parameters
    - _Requirements: 7.4, 7.6_
  
  - [x] 13.3 Add startup instance re-validation
    - Create @EventListener for ApplicationReadyEvent
    - Mark all existing Service Instances as SUSPECT on startup
    - Log count of instances marked for re-validation
    - _Requirements: 9.8_
  
  - [ ]* 13.4 Write property test for distributed lock mutual exclusion
    - **Property 25: Distributed Lock Mutual Exclusion**
    - **Validates: Requirements 6.5**
  
  - [ ]* 13.5 Write property test for fault isolation
    - **Property 30: Fault Isolation**
    - **Validates: Requirements 9.3**
  
  - [ ]* 13.6 Write property test for Redis fallback
    - **Property 31: Redis Fallback**
    - **Validates: Requirements 9.4**

- [ ] 14. Implement API backward compatibility
  - [ ]* 14.1 Write property test for API backward compatibility
    - **Property 26: API Backward Compatibility**
    - **Validates: Requirements 8.1, 8.2, 8.3**
  
  - [ ]* 14.2 Write integration tests for existing API endpoints
    - Test POST /registry/service with existing fields
    - Test POST /registry/instance with existing fields
    - Test POST /registry/heartbeat with host and port params
    - Test GET /registry/instances/{serviceName}
    - Verify responses contain all existing fields
    - _Requirements: 8.4, 8.5_

- [ ] 15. Configuration and documentation
  - [x] 15.1 Create comprehensive application.yml
    - Add all registry.* configuration properties with defaults
    - Add management.endpoints configuration for Actuator
    - Add spring.datasource configuration with connection pooling
    - Add spring.redis configuration
    - Add aws.* configuration properties
    - Document each property with comments
    - _Requirements: 7.1, 7.2, 7.5_
  
  - [x] 15.2 Create application-dev.yml, application-staging.yml, application-prod.yml
    - Configure environment-specific values
    - Use environment variables for sensitive values
    - _Requirements: 7.2_
  
  - [x] 15.3 Add Swagger/OpenAPI configuration
    - Add springdoc-openapi dependency to pom.xml
    - Create OpenAPI configuration bean
    - Add API documentation annotations to RegistryController
    - Verify /swagger-ui.html endpoint is accessible
    - _Requirements: 8.7_
  
  - [ ]* 15.4 Write unit test for configuration validation
    - Test startup fails with missing required properties
    - Test default values are applied for optional properties
    - _Requirements: 7.4, 7.5_

- [ ] 16. Database migration scripts
  - [x] 16.1 Create Flyway migration for schema changes
    - Add Flyway dependency to pom.xml
    - Create V2__add_ec2_and_metrics_support.sql migration
    - Add ec2InstanceId, ec2Region columns to service_instances (nullable)
    - Add createdAt, updatedAt columns to service_instances
    - Add heartbeatThresholdMs, loadBalancingStrategy, serviceVersion to services (nullable)
    - Add recovery policy columns to services (nullable with defaults)
    - _Requirements: 1.7, 1.8, 3.4, 4.1, 5.11_

- [ ] 17. Integration and end-to-end testing
  - [ ]* 17.1 Write integration test for Docker recovery flow
    - Register Docker instance → Miss heartbeats → Detect failure → Recover → Verify UP
    - Verify metrics recorded at each step
    - Verify RecoveryEvent published
    - _Requirements: 1.6, 2.7, 5.9_
  
  - [ ]* 17.2 Write integration test for EC2 recovery flow
    - Register EC2 instance → Miss heartbeats → Detect failure → Recover → Verify UP
    - Mock AWS SDK calls
    - Verify metrics recorded at each step
    - Verify RecoveryEvent published
    - _Requirements: 1.2, 2.7, 5.9_
  
  - [ ]* 17.3 Write integration test for multi-instance scenario
    - Register 10 instances of different service types
    - Simulate different failure patterns
    - Verify independent health evaluation
    - Verify concurrent recovery coordination
    - _Requirements: 3.1, 3.6, 6.5_
  
  - [ ]* 17.4 Write load test for 100+ instances
    - Register 100 Service Instances
    - Measure Failure Detector cycle time (should be < 10s)
    - Measure recovery decision time (should be < 5s)
    - Measure Prometheus endpoint response time (should be < 1s)
    - _Requirements: 6.1, 6.2, 6.7_

- [ ] 18. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 19. Create Grafana dashboard JSON
  - Create grafana-dashboard.json with panels for:
    - Service Instance States (pie chart)
    - Recovery Success Rate (gauge)
    - Heartbeat Latency (histogram)
    - Failure Rate by Service (time series)
    - Restart Count by Platform (bar chart)
    - Circuit Breaker States (status panel)
  - Document dashboard import instructions
  - _Requirements: 2.1_

- [x] 20. Update README and deployment documentation
  - Document new features and capabilities
  - Document configuration properties
  - Document AWS IAM permissions required
  - Document Prometheus and Grafana setup
  - Document database migration steps
  - Document backward compatibility guarantees
  - Document rollback procedures
  - _Requirements: 7.1, 8.1_

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation at key milestones
- Property tests use jqwik framework with minimum 100 iterations
- Integration tests use Spring Boot Test with TestContainers for MySQL and Redis
- AWS SDK calls in tests should be mocked using Mockito or LocalStack
- All property tests must be tagged with format: `Feature: registry-service-enhancements, Property {N}: {title}`
