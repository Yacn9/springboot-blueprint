package com.acme.blueprint.notifications.domain;

import com.acme.blueprint.shared.BusinessRuleViolationException;

public class InvalidNotificationException extends BusinessRuleViolationException {

    InvalidNotificationException(String field, String reason) {
        super("notifications.invalid-content", "Notification field '%s' %s".formatted(field, reason));
    }
}
