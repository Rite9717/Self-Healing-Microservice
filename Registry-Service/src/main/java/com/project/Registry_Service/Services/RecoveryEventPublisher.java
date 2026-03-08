package com.project.Registry_Service.Services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Publisher for recovery events.
 * Publishes events to Spring's event system and triggers notifications.
 */
@Slf4j
@Component
public class RecoveryEventPublisher {
    
    private final ApplicationEventPublisher eventPublisher;
    private final RecoveryNotificationService notificationService;
    
    public RecoveryEventPublisher(ApplicationEventPublisher eventPublisher,
                                 RecoveryNotificationService notificationService) {
        this.eventPublisher = eventPublisher;
        this.notificationService = notificationService;
    }

    /**
     * Publish a recovery event and send notifications
     * 
     * @param event the recovery event to publish
     */
    public void publishEvent(RecoveryEvent event) {
        log.debug("Publishing recovery event: {}", event);
        eventPublisher.publishEvent(event);
        notificationService.notify(event);
    }
}
