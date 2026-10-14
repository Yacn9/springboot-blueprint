package com.acme.blueprint.notifications.application;

import com.acme.blueprint.customers.CustomerRegistered;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Reacts to the customers module's {@link CustomerRegistered} event.
 *
 * <p>{@code @ApplicationModuleListener} runs after the publishing transaction
 * commits, asynchronously, in a new transaction. The publication stays in the
 * registry until this method returns normally; if it throws, it can be
 * resubmitted later — so the listener only translates the event and
 * delegates, and the use case is idempotent.
 */
@Component
class CustomerRegisteredListener {

    private final NotificationManagement notifications;

    CustomerRegisteredListener(NotificationManagement notifications) {
        this.notifications = notifications;
    }

    @ApplicationModuleListener
    void on(CustomerRegistered event) {
        notifications.sendWelcome(event.customerId(), event.fullName(), event.email());
    }
}
