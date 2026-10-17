package com.acme.blueprint.notifications.domain;

import java.util.Optional;

import com.acme.blueprint.users.UserId;

/** Persistence port for notifications, implemented in {@code infrastructure}. */
public interface NotificationRepository {

    /**
     * Stores a new notification and writes it immediately, so database
     * constraints — such as one welcome per user — reject a duplicate
     * here, before the caller sends anything.
     */
    Notification add(Notification notification);

    Optional<Notification> findById(NotificationId id);

    boolean existsByUserAndKind(UserId userId, NotificationKind kind);
}
