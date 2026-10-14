package com.acme.blueprint.notifications.domain;

import java.util.Optional;

import com.acme.blueprint.customers.CustomerId;

/** Persistence port for notifications, implemented in {@code infrastructure}. */
public interface NotificationRepository {

    /**
     * Stores a new notification and writes it immediately, so database
     * constraints — such as one welcome per customer — reject a duplicate
     * here, before the caller sends anything.
     */
    Notification add(Notification notification);

    Optional<Notification> findById(NotificationId id);

    boolean existsByCustomerAndKind(CustomerId customerId, NotificationKind kind);
}
