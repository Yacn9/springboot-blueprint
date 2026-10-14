package com.acme.blueprint.notifications.application;

import com.acme.blueprint.customers.CustomerId;
import com.acme.blueprint.notifications.domain.Notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Read-side port for list screens — the same split as the customers module's
 * {@code CustomerFinder}: paging is an application concern phrased in Spring
 * Data types, which the domain does not import.
 */
public interface NotificationFinder {

    /**
     * Newest first, always.
     *
     * @throws com.acme.blueprint.shared.InvalidSortException if {@code pageable} asks for any sort
     */
    Page<Notification> findForCustomer(CustomerId customerId, Pageable pageable);
}
