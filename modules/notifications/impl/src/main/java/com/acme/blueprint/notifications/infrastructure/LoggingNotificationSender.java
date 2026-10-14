package com.acme.blueprint.notifications.infrastructure;

import com.acme.blueprint.notifications.application.NotificationSender;
import com.acme.blueprint.notifications.domain.Notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Placeholder delivery channel: logs instead of sending. Replace it with an
 * SMTP or SMS adapter implementing {@link NotificationSender}; nothing else
 * changes.
 */
@Component
class LoggingNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationSender.class);

    @Override
    public void send(Notification notification) {
        log.info("Sending {} notification {} to {}: {}",
                notification.kind(), notification.id(), notification.recipient(), notification.subject());
    }
}
