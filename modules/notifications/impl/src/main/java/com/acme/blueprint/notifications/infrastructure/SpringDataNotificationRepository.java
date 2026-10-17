package com.acme.blueprint.notifications.infrastructure;

import java.util.UUID;

import com.acme.blueprint.notifications.domain.Notification;
import com.acme.blueprint.notifications.domain.NotificationKind;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data's view of the notification table. Package-private: only the adapter uses it. */
interface SpringDataNotificationRepository extends JpaRepository<Notification, UUID> {

    /** Served by notification_user_idx; the pageable adds the id tiebreaker. */
    Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    boolean existsByUserIdAndKind(UUID userId, NotificationKind kind);
}
