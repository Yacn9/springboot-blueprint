package com.acme.blueprint.notifications.application;

import com.acme.blueprint.users.UserRegistered;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Reacts to the users module's {@link UserRegistered} event.
 *
 * <p>{@code @ApplicationModuleListener} runs after the publishing transaction
 * commits, asynchronously, in a new transaction. The publication stays in the
 * registry until this method returns normally; if it throws, it can be
 * resubmitted later — so the listener only translates the event and
 * delegates, and the use case is idempotent.
 */
@Component
class UserRegisteredListener {

    private final NotificationManagement notifications;

    UserRegisteredListener(NotificationManagement notifications) {
        this.notifications = notifications;
    }

    @ApplicationModuleListener
    void on(UserRegistered event) {
        notifications.sendWelcome(event.userId(), event.fullName(), event.email());
    }
}
