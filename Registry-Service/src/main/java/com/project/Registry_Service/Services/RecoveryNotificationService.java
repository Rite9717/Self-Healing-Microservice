package com.project.Registry_Service.Services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Service for sending recovery event notifications.
 * Supports multiple notification types: log, webhook, SNS
 */
@Slf4j
@Service
public class RecoveryNotificationService {
    
    @Value("${registry.notification.type:log}")
    private String notificationType;
    
    @Value("${registry.notification.webhook.url:}")
    private String webhookUrl;
    
    private final RestTemplate restTemplate;
    
    public RecoveryNotificationService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Send notification for a recovery event
     * 
     * @param event the recovery event to notify about
     */
    public void notify(RecoveryEvent event) {
        switch (notificationType.toLowerCase()) {
            case "webhook" -> sendWebhook(event);
            case "sns" -> sendSNS(event);
            default -> logEvent(event);
        }
    }

    /**
     * Log the recovery event
     */
    private void logEvent(RecoveryEvent event) {
        if (event.success()) {
            log.info("Recovery successful: service={}, host={}:{}, action={}, message={}", 
                event.serviceName(), event.host(), event.port(), event.action(), event.message());
        } else {
            log.error("Recovery failed: service={}, host={}:{}, message={}", 
                event.serviceName(), event.host(), event.port(), event.message());
        }
    }

    /**
     * Send recovery event to webhook
     */
    private void sendWebhook(RecoveryEvent event) {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            log.warn("Webhook URL not configured, falling back to log notification");
            logEvent(event);
            return;
        }
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // Create webhook payload
            WebhookPayload payload = new WebhookPayload(
                event.serviceName(),
                event.host(),
                event.port(),
                event.action() != null ? event.action().name() : null,
                event.success(),
                event.message(),
                event.timestamp().toString()
            );
            
            HttpEntity<WebhookPayload> request = new HttpEntity<>(payload, headers);
            
            restTemplate.postForEntity(webhookUrl, request, String.class);
            
            log.debug("Webhook notification sent for recovery event: {}", event.serviceName());
            
        } catch (Exception e) {
            log.error("Failed to send webhook notification: {}", e.getMessage());
            // Fallback to log
            logEvent(event);
        }
    }

    /**
     * Send recovery event to AWS SNS (stub for future implementation)
     */
    private void sendSNS(RecoveryEvent event) {
        log.warn("SNS notification not yet implemented, falling back to log notification");
        logEvent(event);
        
        // TODO: Implement AWS SNS integration
        // - Create SNS client
        // - Format message
        // - Publish to topic
    }

    /**
     * Webhook payload record
     */
    private record WebhookPayload(
        String serviceName,
        String host,
        int port,
        String action,
        boolean success,
        String message,
        String timestamp
    ) {}
}
