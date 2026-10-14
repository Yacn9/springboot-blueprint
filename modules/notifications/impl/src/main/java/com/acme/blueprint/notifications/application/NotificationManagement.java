package com.acme.blueprint.notifications.application;

import java.time.Clock;

import com.acme.blueprint.customers.CustomerDirectory;
import com.acme.blueprint.customers.CustomerId;
import com.acme.blueprint.customers.CustomerSummary;
import com.acme.blueprint.notifications.domain.Notification;
import com.acme.blueprint.notifications.domain.NotificationId;
import com.acme.blueprint.notifications.domain.NotificationKind;
import com.acme.blueprint.notifications.domain.NotificationRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases of the notifications module. Shows both ways modules talk:
 * {@link #sendWelcome} is driven by another module's event, while
 * {@link #sendToCustomer} calls the customers module synchronously through
 * its published {@link CustomerDirectory} contract.
 */
@Service
@Transactional
public class NotificationManagement {

    private final NotificationRepository notifications;
    private final NotificationFinder finder;
    private final NotificationSender sender;
    private final CustomerDirectory customers;
    private final Clock clock;

    NotificationManagement(
            NotificationRepository notifications,
            NotificationFinder finder,
            NotificationSender sender,
            CustomerDirectory customers,
            Clock clock) {
        this.notifications = notifications;
        this.finder = finder;
        this.sender = sender;
        this.customers = customers;
        this.clock = clock;
    }

    /**
     * Idempotent: the triggering event is delivered at least once, so a
     * redelivery must not greet the customer twice. The check handles the
     * normal case; if two deliveries race past it, the second row is rejected
     * by a unique index when {@link #deliver} writes it — before anything is
     * sent — and that delivery is retried later as a no-op.
     *
     * <p>Parameters follow {@code CustomerRegistered}'s order.
     */
    public void sendWelcome(CustomerId customerId, String fullName, String email) {
        if (notifications.existsByCustomerAndKind(customerId, NotificationKind.WELCOME)) {
            return;
        }
        deliver(Notification.welcome(customerId, fullName, email, clock.instant()));
    }

    public Notification sendToCustomer(CustomerId customerId, String subject, String body) {
        CustomerSummary customer = customers.findById(customerId)
                .orElseThrow(() -> new UnknownCustomerException(customerId));
        return deliver(Notification.adHoc(customerId, subject, body, customer.email(), clock.instant()));
    }

    @Transactional(readOnly = true)
    public Notification get(NotificationId notificationId) {
        return notifications.findById(notificationId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));
    }

    /** Newest first. */
    @Transactional(readOnly = true)
    public Page<Notification> findForCustomer(CustomerId customerId, Pageable pageable) {
        return finder.findForCustomer(customerId, pageable);
    }

    /**
     * The row is written (and its constraints checked) before the message
     * leaves; if sending fails, the transaction rolls back and the row goes
     * with it. If the commit itself fails after a successful send, a retry
     * sends again — delivery is at-least-once, see {@link NotificationSender}.
     */
    private Notification deliver(Notification notification) {
        Notification stored = notifications.add(notification);
        sender.send(stored);
        stored.markSent(clock.instant());
        return stored;
    }
}
