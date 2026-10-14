package com.acme.blueprint.notifications.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Typed notification identifier. Lives in {@code domain} because no other
 * module refers to notifications yet; move it to notifications-api the day
 * one does — aggregate ids are always typed, and the type sits in -api only
 * once it crosses a module boundary.
 */
public record NotificationId(UUID value) {

    public NotificationId {
        Objects.requireNonNull(value, "value");
    }

    public static NotificationId newId() {
        return new NotificationId(UUID.randomUUID());
    }

    /** Also the factory Spring MVC uses to bind {@code {notificationId}} path variables. */
    public static NotificationId of(String value) {
        return new NotificationId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
