package com.acme.blueprint.notifications.application;

import com.acme.blueprint.users.UserId;
import com.acme.blueprint.notifications.domain.Notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Read-side port for list screens — the same split as the users module's
 * {@code UserFinder}: paging is an application concern phrased in Spring
 * Data types, which the domain does not import.
 */
public interface NotificationFinder {

    /**
     * Newest first, always.
     *
     * @throws com.acme.blueprint.shared.InvalidSortException if {@code pageable} asks for any sort
     */
    Page<Notification> findForUser(UserId userId, Pageable pageable);
}
