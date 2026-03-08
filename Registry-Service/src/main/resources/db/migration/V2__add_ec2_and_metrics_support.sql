-- Migration V2: Add EC2 and metrics support
-- Adds fields for EC2 recovery, timestamps, and enhanced service configuration

-- Add EC2-specific fields to service_instances table
ALTER TABLE service_instances
ADD COLUMN IF NOT EXISTS ec2_instance_id VARCHAR(255),
ADD COLUMN IF NOT EXISTS ec2_region VARCHAR(50),
ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

-- Add enhanced configuration fields to services table
ALTER TABLE services
ADD COLUMN IF NOT EXISTS heartbeat_threshold_ms BIGINT,
ADD COLUMN IF NOT EXISTS load_balancing_strategy VARCHAR(50),
ADD COLUMN IF NOT EXISTS service_version VARCHAR(50);

-- Add recovery policy fields to services table
ALTER TABLE services
ADD COLUMN IF NOT EXISTS max_restart_attempts INT DEFAULT 3,
ADD COLUMN IF NOT EXISTS quarantine_duration_ms BIGINT DEFAULT 1200000,
ADD COLUMN IF NOT EXISTS preferred_recovery_actions VARCHAR(255) DEFAULT 'RESTART,START';

-- Add default value for platform if not set
UPDATE services SET platform = 'docker' WHERE platform IS NULL OR platform = '';

-- Create index for faster state-based queries
CREATE INDEX IF NOT EXISTS idx_service_instances_state ON service_instances(state);

-- Create index for faster service name queries
CREATE INDEX IF NOT EXISTS idx_service_instances_service_name ON service_instances(service_id);

-- Create index for EC2 instance lookups
CREATE INDEX IF NOT EXISTS idx_service_instances_ec2_id ON service_instances(ec2_instance_id);
