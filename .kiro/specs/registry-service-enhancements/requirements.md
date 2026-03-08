# Requirements Document

## Introduction

The Sentinel Self-Healing Microservice Registry is a Spring Boot application that monitors microservices, detects failures, and automatically recovers failed instances. This document specifies requirements for enhancing the Registry Service with AWS EC2 recovery integration, observability metrics, multi-instance support, enhanced failure detection, and improved recovery strategies. These enhancements must be production-ready before EC2 deployment while maintaining backward compatibility with existing Docker-based recovery.

## Glossary

- **Registry_Service**: The central service that tracks, monitors, and recovers microservice instances
- **Service_Instance**: A running instance of a microservice registered with the Registry Service
- **Heartbeat**: A periodic signal sent by a Service Instance to indicate it is operational
- **Recovery_Strategy**: A pluggable mechanism for recovering failed Service Instances (Docker, AWS EC2, Kubernetes)
- **Failure_Detector**: The component that monitors heartbeats and determines Service Instance health
- **Docker_Recovery**: Recovery strategy that restarts Docker containers
- **EC2_Recovery**: Recovery strategy that manages AWS EC2 instances (reboot, start, stop)
- **Prometheus**: Time-series database and monitoring system for collecting metrics
- **Grafana**: Visualization platform for displaying metrics dashboards
- **Circuit_Breaker**: Pattern that prevents repeated recovery attempts on persistently failing instances
- **Distributed_Lock**: Redis-based locking mechanism to coordinate recovery across multiple Registry Service instances
- **Service_Type**: A category of microservice (e.g., ServiceA, ServiceB, ServiceC)
- **Health_Check**: An endpoint or mechanism to verify Service Instance operational status
- **Grace_Period**: Time window during which new Service Instances are not subject to failure detection
- **Quarantine**: State where a Service Instance is temporarily excluded from recovery attempts
- **Recovery_Policy**: Configuration that defines recovery behavior for a specific Service Type

## Requirements

### Requirement 1: AWS EC2 Recovery Integration

**User Story:** As a system administrator, I want the Registry Service to recover failed EC2 instances using AWS SDK operations, so that I can manage cloud-based microservices with the same self-healing capabilities as Docker containers.

#### Acceptance Criteria

1. WHEN a Service Instance is configured with platform "ec2", THE Registry_Service SHALL use AWS SDK for Java v2 (software.amazon.awssdk.ec2) for recovery operations
2. WHEN recovering an EC2-based Service Instance, THE EC2_Recovery SHALL support reboot, start, and stop operations based on instance state
3. WHEN an EC2 instance is in "stopped" state, THE EC2_Recovery SHALL execute start operation
4. WHEN an EC2 instance is in "running" state, THE EC2_Recovery SHALL execute reboot operation
5. WHEN EC2 recovery operations fail, THE Registry_Service SHALL log detailed error information including AWS error codes
6. THE Registry_Service SHALL maintain backward compatibility with Docker_Recovery for instances configured with platform "docker"
7. WHEN a Service Instance is registered, THE Registry_Service SHALL accept an "ec2InstanceId" field for EC2-based instances
8. WHEN a Service Instance is registered, THE Registry_Service SHALL accept a "platform" field with values "docker" or "ec2"

### Requirement 2: Observability and Metrics Collection

**User Story:** As a DevOps engineer, I want comprehensive metrics exposed via Prometheus and visualized in Grafana, so that I can monitor registry health, track service uptime, and analyze failure patterns.

#### Acceptance Criteria

1. THE Registry_Service SHALL integrate Micrometer with Prometheus registry for metrics collection
2. THE Registry_Service SHALL expose a metrics endpoint at "/actuator/prometheus" for Prometheus scraping
3. WHEN a Service Instance transitions to UP state, THE Registry_Service SHALL increment a counter metric "registry_service_uptime_total" tagged with service name
4. WHEN a Service Instance is restarted, THE Registry_Service SHALL increment a counter metric "registry_restart_count_total" tagged with service name and platform
5. WHEN a Service Instance transitions to UNRESPONSIVE state, THE Registry_Service SHALL increment a counter metric "registry_failure_count_total" tagged with service name
6. WHEN a heartbeat is received, THE Registry_Service SHALL record a histogram metric "registry_heartbeat_latency_seconds" measuring time since last heartbeat
7. WHEN a recovery operation completes, THE Registry_Service SHALL increment a counter metric "registry_recovery_success_total" or "registry_recovery_failure_total" tagged with service name, platform, and recovery action
8. THE Registry_Service SHALL expose a gauge metric "registry_instances_by_state" showing count of instances in each state (UP, SUSPECT, UNRESPONSIVE, QUARANTINED)
9. THE Registry_Service SHALL expose a gauge metric "registry_registered_services_total" showing total count of registered Service Types
10. THE Registry_Service SHALL expose a gauge metric "registry_registered_instances_total" showing total count of registered Service Instances

### Requirement 3: Multi-Instance Support

**User Story:** As a microservices architect, I want the Registry Service to track multiple instances of multiple service types, so that I can manage a diverse microservices ecosystem with proper service discovery.

#### Acceptance Criteria

1. WHEN multiple Service Instances of the same Service Type are registered, THE Registry_Service SHALL track each instance independently with unique identifiers
2. WHEN querying for healthy instances by service name, THE Registry_Service SHALL return all instances in UP state for that Service Type
3. THE Registry_Service SHALL support registration of different Service Types (ServiceA, ServiceB, ServiceC, etc.) with independent configurations
4. WHEN a Service Type is registered, THE Registry_Service SHALL accept optional metadata fields including "loadBalancingStrategy" and "serviceVersion"
5. THE Registry_Service SHALL provide an endpoint "/registry/instances/{serviceName}" that returns all healthy instances for service discovery
6. WHEN multiple instances of the same Service Type exist, THE Failure_Detector SHALL evaluate each instance's health independently
7. THE Registry_Service SHALL support at least 100 concurrent Service Instances across all Service Types
8. WHEN a Service Instance is registered, THE Registry_Service SHALL validate that the combination of host and port is unique across all instances

### Requirement 4: Enhanced Failure Detection

**User Story:** As a reliability engineer, I want configurable failure detection with grace periods and health checks, so that I can tune monitoring sensitivity per service and avoid false positives for new instances.

#### Acceptance Criteria

1. WHEN a Service Type is registered, THE Registry_Service SHALL accept a "heartbeatThresholdMs" configuration parameter
2. WHEN evaluating Service Instance health, THE Failure_Detector SHALL use the Service Type's configured heartbeatThresholdMs instead of a global threshold
3. WHEN a Service Instance is newly registered, THE Failure_Detector SHALL apply a grace period of 60 seconds before marking it as SUSPECT
4. WHEN a Service Instance has a configured healthPath, THE Failure_Detector SHALL perform HTTP health checks in addition to heartbeat monitoring
5. WHEN a health check endpoint returns HTTP 200, THE Registry_Service SHALL consider the instance healthy regardless of heartbeat status
6. WHEN a health check endpoint returns non-200 status or times out, THE Registry_Service SHALL increment missed health check counter
7. WHEN a Service Instance misses both heartbeats and health checks, THE Failure_Detector SHALL transition it to SUSPECT state
8. THE Registry_Service SHALL expose configuration properties for SUSPECT_THRESHOLD and UNRESPONSIVE_THRESHOLD per Service Type

### Requirement 5: Recovery Strategy Improvements

**User Story:** As a platform engineer, I want pluggable recovery strategies with circuit breakers and notifications, so that I can prevent cascading failures and stay informed about recovery events.

#### Acceptance Criteria

1. THE Registry_Service SHALL implement a Recovery_Strategy interface with methods: canRecover(), recover(), and getName()
2. WHEN a Service Instance requires recovery, THE Recovery_Service SHALL select the appropriate Recovery_Strategy based on the instance's platform configuration
3. THE Registry_Service SHALL support registering custom Recovery_Strategy implementations via Spring dependency injection
4. WHEN a Service Instance fails recovery more than 3 times within 20 minutes, THE Circuit_Breaker SHALL open and prevent further recovery attempts
5. WHEN a Circuit_Breaker is open, THE Registry_Service SHALL transition the Service Instance to QUARANTINED state
6. WHEN a Circuit_Breaker has been open for the configured quarantine duration, THE Circuit_Breaker SHALL transition to half-open state allowing one recovery attempt
7. WHEN a recovery attempt succeeds in half-open state, THE Circuit_Breaker SHALL close and reset failure counters
8. WHEN a recovery attempt fails in half-open state, THE Circuit_Breaker SHALL reopen and extend quarantine duration
9. WHEN a recovery operation completes (success or failure), THE Registry_Service SHALL publish a RecoveryEvent containing instance details, recovery action, outcome, and timestamp
10. THE Registry_Service SHALL provide a configurable notification mechanism (webhook, SNS, or log-based) for RecoveryEvent notifications
11. WHEN a Service Type is registered, THE Registry_Service SHALL accept a Recovery_Policy configuration specifying maxRestartAttempts, quarantineDurationMs, and preferredRecoveryActions
12. WHEN multiple recovery actions are available for a platform, THE Recovery_Service SHALL attempt actions in the order specified by the Recovery_Policy

### Requirement 6: Performance and Scalability

**User Story:** As a system architect, I want the Registry Service to handle 100+ instances with sub-5-second recovery decisions, so that the system scales to production workloads without performance degradation.

#### Acceptance Criteria

1. WHEN 100 Service Instances are registered, THE Failure_Detector SHALL complete a full health check cycle within 10 seconds
2. WHEN a Service Instance transitions to UNRESPONSIVE state, THE Recovery_Service SHALL make a recovery decision within 5 seconds
3. THE Registry_Service SHALL use connection pooling for database operations to support concurrent instance updates
4. THE Registry_Service SHALL use Redis distributed locking with a maximum lock acquisition timeout of 2 seconds
5. WHEN multiple Registry Service instances are running, THE Distributed_Lock SHALL ensure only one instance attempts recovery for a given Service Instance
6. THE Registry_Service SHALL batch database updates for Service Instance state changes when possible
7. WHEN Prometheus scrapes the metrics endpoint, THE Registry_Service SHALL respond within 1 second

### Requirement 7: Configuration and Deployment

**User Story:** As a DevOps engineer, I want externalized configuration for all thresholds and strategies, so that I can tune the Registry Service for different environments without code changes.

#### Acceptance Criteria

1. THE Registry_Service SHALL externalize all configuration parameters via Spring Boot application.properties or application.yml
2. THE Registry_Service SHALL support environment-specific configuration profiles (dev, staging, production)
3. WHEN AWS credentials are required, THE Registry_Service SHALL support AWS credential provider chain (environment variables, IAM roles, credential files)
4. THE Registry_Service SHALL validate all required configuration parameters at startup and fail fast with descriptive error messages
5. THE Registry_Service SHALL provide default values for optional configuration parameters
6. THE Registry_Service SHALL log the active configuration profile and key parameter values at startup (excluding sensitive credentials)
7. WHEN deployed to EC2, THE Registry_Service SHALL use IAM instance profile for AWS SDK authentication

### Requirement 8: API Compatibility and Versioning

**User Story:** As a service developer, I want existing API contracts maintained, so that my microservices continue working without modification after the Registry Service upgrade.

#### Acceptance Criteria

1. THE Registry_Service SHALL maintain all existing REST endpoints with unchanged request/response formats
2. WHEN new fields are added to request DTOs, THE Registry_Service SHALL treat them as optional to maintain backward compatibility
3. WHEN new fields are added to response DTOs, THE Registry_Service SHALL include them without removing existing fields
4. THE Registry_Service SHALL continue accepting heartbeat requests at "/registry/heartbeat" with host and port parameters
5. THE Registry_Service SHALL continue accepting instance registration at "/registry/instance" with existing InstanceRegisterRequest fields
6. WHEN a Service Instance is registered without platform field, THE Registry_Service SHALL default to "docker" for backward compatibility
7. THE Registry_Service SHALL provide API documentation via Swagger/OpenAPI at "/swagger-ui.html"

### Requirement 9: Error Handling and Resilience

**User Story:** As a reliability engineer, I want robust error handling and graceful degradation, so that Registry Service failures don't cascade to monitored services.

#### Acceptance Criteria

1. WHEN AWS SDK operations fail with transient errors, THE EC2_Recovery SHALL retry up to 3 times with exponential backoff
2. WHEN AWS SDK operations fail with non-retryable errors, THE EC2_Recovery SHALL log the error and transition the instance to QUARANTINED state
3. WHEN database operations fail, THE Registry_Service SHALL log the error and continue processing other Service Instances
4. WHEN Redis is unavailable, THE Registry_Service SHALL fall back to in-memory locking with a warning log message
5. WHEN a Recovery_Strategy throws an exception, THE Recovery_Service SHALL catch it, log details, and mark the recovery attempt as failed
6. WHEN health check HTTP requests timeout, THE Failure_Detector SHALL treat them as failed checks without blocking other instance evaluations
7. THE Registry_Service SHALL implement circuit breakers for external dependencies (AWS SDK, health check endpoints)
8. WHEN the Registry Service starts up, THE Registry_Service SHALL mark all existing Service Instances as SUSPECT to trigger re-validation

### Requirement 10: Testing and Validation

**User Story:** As a quality engineer, I want comprehensive test coverage including property-based tests, so that I can verify correctness across diverse inputs and edge cases.

#### Acceptance Criteria

1. THE Registry_Service SHALL include unit tests for all Recovery_Strategy implementations
2. THE Registry_Service SHALL include integration tests that verify end-to-end recovery flows for both Docker and EC2 platforms
3. THE Registry_Service SHALL include property-based tests that verify state transition invariants for Service Instances
4. THE Registry_Service SHALL include tests that verify metrics are correctly recorded for all recovery operations
5. THE Registry_Service SHALL include tests that verify distributed locking prevents concurrent recovery attempts
6. THE Registry_Service SHALL include tests that verify Circuit_Breaker state transitions under various failure scenarios
7. THE Registry_Service SHALL include tests that verify backward compatibility with existing API contracts
8. THE Registry_Service SHALL include load tests that verify performance requirements with 100+ Service Instances
