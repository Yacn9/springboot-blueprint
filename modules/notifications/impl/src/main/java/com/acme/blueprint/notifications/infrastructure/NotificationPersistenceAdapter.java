package com.acme.blueprint.notifications.infrastructure;

import java.util.Map;
import java.util.Optional;

import com.acme.blueprint.users.UserId;
import com.acme.blueprint.notifications.application.NotificationFinder;
import com.acme.blueprint.notifications.domain.Notification;
import com.acme.blueprint.notifications.domain.NotificationId;
import com.acme.blueprint.notifications.domain.NotificationKind;
import com.acme.blueprint.notifications.domain.NotificationRepository;
import com.acme.blueprint.shared.SortMapping;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/**
 * Adapts Spring Data JPA to the domain's {@link NotificationRepository} and
 * the application's {@link NotificationFinder}.
 */
@Repository
class NotificationPersistenceAdapter implements NotificationRepository, NotificationFinder {

    /** Fixed order (newest first, in the query): no public sort keys, only the id tiebreaker. */
    private static final SortMapping SORTING = SortMapping.of(Map.of(), "id");

    private final SpringDataNotificationRepository jpa;

    NotificationPersistenceAdapter(SpringDataNotificationRepository jpa) {
        this.jpa = jpa;
    }

    /** Flushes, so the one-welcome-per-user index fires before anything is sent. */
    @Override
    public Notification add(Notification notification) {
        return jpa.saveAndFlush(notification);
    }

    @Override
    public Optional<Notification> findById(NotificationId id) {
        return jpa.findById(id.value());
    }

    @Override
    public boolean existsByUserAndKind(UserId userId, NotificationKind kind) {
        return jpa.existsByUserIdAndKind(userId.value(), kind);
    }

    @Override
    public Page<Notification> findForUser(UserId userId, Pageable pageable) {
        return jpa.findByUserIdOrderByCreatedAtDesc(userId.value(), SORTING.translate(pageable));
    }
}
