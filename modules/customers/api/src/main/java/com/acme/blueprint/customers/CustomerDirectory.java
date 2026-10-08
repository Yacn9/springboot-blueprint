package com.acme.blueprint.customers;

import java.util.Optional;

/**
 * Synchronous, read-only access to customers for other modules.
 *
 * <p>Prefer reacting to {@link CustomerRegistered} when a module only needs to
 * know that something happened; call the directory when it needs current
 * data at the moment of a request.
 */
public interface CustomerDirectory {

    Optional<CustomerSummary> findById(CustomerId id);
}
