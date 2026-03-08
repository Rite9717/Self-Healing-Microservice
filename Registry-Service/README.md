# Sentinel Registry Service

A self-healing microservice registry with automatic failure detection and recovery capabilities. Supports both Docker and AWS EC2 platforms with comprehensive observability through Prometheus metrics.

## Features

### Core Capabilities
- **Service Registration & Discovery**: Register microservices and discover healthy instances
- **Heartbeat Monitoring**: Continuous health monitoring with configurable thresholds
- **Automatic Failure Detection**: Multi-stage detection (UP → SUSPECT → UNRESPONSIVE)
- **Self-Healing Recovery**: Automatic restart of failed instances
- **Circuit Breaker Protection**: Prevents cascading failures with quarantine mechanism
- **Distributed Locking**: Redis-based coordination for multi-registry deployments

### Platform Support
- **Docker**: Container restart and management
- **AWS EC2**: Instance reboot, start, and stop operations
- **Extensible**: Pluggable recovery strategy interface for future platforms

### Observability
- **Prometheus Metrics**: Comprehensive metrics for monitoring
- **Grafana Ready**: Pre-configured dashboards available
- **Health Checks**: HTTP endpoint validation in addition to heartbeats
- **Event Notifications**: Webhook, SNS, or log-based notifications

## Quick Start

### Prerequisites
- Java 21
- MySQL 8.0+
- Redis 6.0+
- Docker (for Docker recovery)
- AWS credentials (for EC2 recovery)

### Running Locally

1. **Start dependencies**:
```bash
docker-compose up -d mysql redis
```

2. **Configure application**:
```yaml
# application-dev.yml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/sentinel_dev
    username: root
    password: root
  redis:
    host: localhost
    port: 6379
```

3. **Run the service**:
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

4. **Access endpoints**:
- API: http://localhost:8081
- Swagger UI: http://localhost:8081/swagger-ui.html
- Prometheus Metrics: http://localhost:8081/actuator/prometheus
- Health: http://localhost:8081/actuator/health

## Configuration

### Environment Profiles

- **dev**: Local development with Docker recovery
- **staging**: Staging environment with EC2 recovery
- **prod**: Production environment with full features

Activate a profile:
```bash
java -jar registry-service.jar --spring.profiles.active=prod
```

### Key Configuration Properties

```yaml
registry:
  recovery:
    ec2:
      enabled: true  # Enable EC2 recovery
    default:
      max-restart-attempts: 3
      quarantine-duration-ms: 1200000  # 20 minutes
  
  notification:
    type: webhook  # log, webhook, or sns
    webhook:
      url: https://your-webhook-url
  
  failure-detection:
    default-heartbeat-threshold-ms: 30000  # 30 seconds
    grace-period-ms: 60000  # 60 seconds for new instances
```

### AWS Configuration

For EC2 recovery, configure AWS credentials using one of:

1. **Environment variables**:
```bash
export AWS_ACCESS_KEY_ID=your-key
export AWS_SECRET_ACCESS_KEY=your-secret
export AWS_REGION=us-east-1
```

2. **IAM Instance Profile** (recommended for EC2 deployment)

3. **AWS credentials file** (~/.aws/credentials)

## API Documentation

### Register a Service

```bash
POST /registry/service
Content-Type: application/json

{
  "name": "ServiceA",
  "platform": "docker",
  "heartbeatThresholdMs": 30000
}
```

### Register a Service Instance

```bash
POST /registry/instance
Content-Type: application/json

{
  "serviceName": "ServiceA",
  "host": "10.0.0.5",
  "port": 8080,
  "baseUrl": "http://10.0.0.5:8080",
  "healthPath": "/actuator/health",
  "containerName": "service-a-container",  # For Docker
  "ec2InstanceId": "i-1234567890abcdef0",  # For EC2
  "ec2Region": "us-east-1"
}
```

### Send Heartbeat

```bash
POST /registry/heartbeat?host=10.0.0.5&port=8080
```

### Discover Healthy Instances

```bash
GET /registry/instances/ServiceA
```

## Prometheus Metrics

### Available Metrics

| Metric | Type | Description |
|--------|------|-------------|
| `registry_instances_by_state` | Gauge | Count of instances by state (UP, SUSPECT, UNRESPONSIVE, QUARANTINED) |
| `registry_heartbeat_latency` | Timer | Time since last heartbeat |
| `registry_failure_count_total` | Counter | Total instance failures |
| `registry_recovery_success_total` | Counter | Successful recovery operations |
| `registry_recovery_failure_total` | Counter | Failed recovery operations |
| `registry_restart_count_total` | Counter | Total instance restarts |
| `registry_registered_services_total` | Gauge | Total registered service types |
| `registry_registered_instances_total` | Gauge | Total registered instances |

### Prometheus Configuration

```yaml
scrape_configs:
  - job_name: 'registry-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['localhost:8081']
```

## Architecture

### State Machine

```
UP → SUSPECT → UNRESPONSIVE → QUARANTINED
 ↑      ↓           ↓              ↓
 └──────┴───────────┴──────────────┘
```

- **UP**: Instance is healthy
- **SUSPECT**: Missed heartbeats, under observation
- **UNRESPONSIVE**: Multiple missed heartbeats, recovery triggered
- **QUARANTINED**: Exceeded max restart attempts, temporarily excluded

### Recovery Flow

1. **Detection**: Failure detector identifies unresponsive instance
2. **Locking**: Acquire distributed lock to prevent concurrent recovery
3. **Circuit Breaker**: Check if circuit breaker allows recovery
4. **Strategy Selection**: Choose appropriate recovery strategy (Docker/EC2)
5. **Execution**: Perform recovery action (restart, start, stop)
6. **Notification**: Publish recovery event
7. **Metrics**: Record recovery outcome

## Database Schema

### Services Table
- `id`: Primary key
- `name`: Service name (unique)
- `platform`: docker, ec2, kubernetes
- `heartbeat_threshold_ms`: Custom heartbeat threshold
- `max_restart_attempts`: Recovery policy
- `quarantine_duration_ms`: Quarantine duration
- `preferred_recovery_actions`: Comma-separated actions

### Service Instances Table
- `id`: Primary key
- `service_id`: Foreign key to services
- `host`, `port`: Instance location (unique together)
- `container_name`: Docker container name
- `ec2_instance_id`, `ec2_region`: EC2 instance details
- `state`: Current state (UP, SUSPECT, UNRESPONSIVE, QUARANTINED)
- `last_heart_beat`: Last heartbeat timestamp
- `created_at`, `updated_at`: Timestamps

## Development

### Building

```bash
./mvnw clean package
```

### Running Tests

```bash
./mvnw test
```

### Database Migrations

Flyway migrations are applied automatically on startup. To run manually:

```bash
./mvnw flyway:migrate
```

## Deployment

### Docker Deployment

```dockerfile
FROM openjdk:21-jdk-slim
COPY target/Registry-Service-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

```bash
docker build -t registry-service .
docker run -p 8081:8081 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_URL=jdbc:mysql://db:3306/sentinel \
  -e DB_USERNAME=root \
  -e DB_PASSWORD=secret \
  registry-service
```

### EC2 Deployment

1. **Create IAM Role** with EC2 permissions:
```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": [
      "ec2:DescribeInstances",
      "ec2:RebootInstances",
      "ec2:StartInstances",
      "ec2:StopInstances"
    ],
    "Resource": "*"
  }]
}
```

2. **Attach role to EC2 instance**

3. **Deploy application** with production profile

## Troubleshooting

### Common Issues

**Issue**: Redis connection failed
- **Solution**: Check Redis is running and accessible. Service will fall back to in-memory locking with a warning.

**Issue**: EC2 recovery not working
- **Solution**: Verify `registry.recovery.ec2.enabled=true` and AWS credentials are configured correctly.

**Issue**: Instances stuck in QUARANTINED state
- **Solution**: Check circuit breaker configuration and quarantine duration. Instances will auto-recover after quarantine period.

### Logging

Enable debug logging:
```yaml
logging:
  level:
    com.project.Registry_Service: DEBUG
    com.project.Registry_Service.Scheduler.FailureDetector: TRACE
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests
5. Submit a pull request

## License

Apache 2.0

## Support

For issues and questions, please open a GitHub issue.
