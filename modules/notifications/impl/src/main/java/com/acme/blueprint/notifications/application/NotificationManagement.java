package com.acme.blueprint.notifications.application;

import java.time.Clock;

import com.acme.blueprint.users.UserDirectory;
import com.acme.blueprint.users.UserId;
import com.acme.blueprint.users.UserSummary;
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
 * {@link #sendToUser} calls the users module synchronously through
 * its published {@link UserDirectory} contract.
 */
@Service
@Transactional
public class NotificationManagement {

    private final NotificationRepository notifications;
    private final NotificationFinder finder;
    private final NotificationSender sender;
    private final UserDirectory users;
    private final Clock clock;

    NotificationManagement(
            NotificationRepository notifications,
            NotificationFinder finder,
            NotificationSender sender,
            UserDirectory users,
            Clock clock) {
        this.notifications = notifications;
        this.finder = finder;
        this.sender = sender;
        this.users = users;
        this.clock = clock;
    }

    /**
     * Idempotent: the triggering event is delivered at least once, so a
     * redelivery must not greet the user twice. The check handles the
     * normal case; if two deliveries race past it, the second row is rejected
     * by a unique index when {@link #deliver} writes it — before anything is
     * sent — and that delivery is retried later as a no-op.
     *
     * <p>Parameters follow {@code UserRegistered}'s order.
     */
    public void sendWelcome(UserId userId, String fullName, String email) {
        if (notifications.existsByUserAndKind(userId, NotificationKind.WELCOME)) {
            return;
        }
        deliver(Notification.welcome(userId, fullName, email, clock.instant()));
    }

    public Notification sendToUser(UserId userId, String subject, String body) {
        UserSummary user = users.findById(userId)
                .orElseThrow(() -> new UnknownUserException(userId));
        return deliver(Notification.adHoc(userId, subject, body, user.email(), clock.instant()));
    }

    @Transactional(readOnly = true)
    public Notification get(NotificationId notificationId) {
        return notifications.findById(notificationId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));
    }

    /** Newest first. */
    @Transactional(readOnly = true)
    public Page<Notification> findForUser(UserId userId, Pageable pageable) {
        return finder.findForUser(userId, pageable);
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
