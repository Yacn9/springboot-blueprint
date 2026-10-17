package com.acme.blueprint.notifications.web;

import java.util.UUID;

import com.acme.blueprint.notifications.domain.Notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/notifications}. The recipient address is looked up,
 * never supplied. Limits come from {@link Notification}.
 */
record SendNotificationRequest(
        @NotNull UUID userId,
        @NotBlank @Size(max = Notification.SUBJECT_MAX_LENGTH) String subject,
        @NotBlank @Size(max = Notification.BODY_MAX_LENGTH) String body) {
}
