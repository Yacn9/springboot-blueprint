package com.acme.blueprint.customers;

import java.time.Instant;

/**
 * Published once a customer has been registered and the transaction commits.
 * It carries the data listeners typically need, so they rarely have to call
 * back into {@link CustomerDirectory}.
 *
 * <p>Delivery is at-least-once (Spring Modulith's event publication registry
 * retries incomplete publications), so listeners must be idempotent.
 */
public record CustomerRegistered(CustomerId customerId, String fullName, String email, Instant registeredAt) {
}
