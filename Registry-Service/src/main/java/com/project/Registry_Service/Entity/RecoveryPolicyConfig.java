package com.project.Registry_Service.Entity;

import jakarta.persistence.Embeddable;
import lombok.Data;

@Embeddable
@Data
public class RecoveryPolicyConfig {
    private Integer maxRestartAttempts = 3;
    private Long quarantineDurationMs = 1_200_000L; // 20 minutes
    private String preferredRecoveryActions = "RESTART,START"; // Comma-separated
}
