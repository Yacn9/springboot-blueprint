package com.acme.blueprint.notifications.web;

import java.time.Instant;
import java.util.UUID;

import com.acme.blueprint.notifications.domain.Notification;
import com.acme.blueprint.notifications.domain.NotificationKind;

record NotificationResponse(
        UUID id,
        UUID userId,
        NotificationKind kind,
        String recipient,
        String subject,
        String body,
        Instant createdAt,
        Instant sentAt) {

    static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.id().value(),
                notification.userId().value(),
                notification.kind(),
                notification.recipient(),
                notification.subject(),
                notification.body(),
                notification.createdAt(),
                notification.sentAt());
    }
}
