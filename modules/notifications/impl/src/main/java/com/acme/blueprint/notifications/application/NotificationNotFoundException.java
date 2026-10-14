package com.acme.blueprint.notifications.application;

import com.acme.blueprint.notifications.domain.NotificationId;
import com.acme.blueprint.shared.ResourceNotFoundException;

public class NotificationNotFoundException extends ResourceNotFoundException {

    NotificationNotFoundException(NotificationId notificationId) {
        super("notifications.not-found", "Notification %s does not exist".formatted(notificationId));
    }
}
