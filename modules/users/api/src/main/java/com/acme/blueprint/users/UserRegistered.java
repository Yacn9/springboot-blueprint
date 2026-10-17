package com.acme.blueprint.users;

import java.time.Instant;

/**
 * Published once a user has been registered and the transaction commits.
 * It carries the data listeners typically need, so they rarely have to call
 * back into {@link UserDirectory}.
 *
 * <p>Delivery is at-least-once (Spring Modulith's event publication registry
 * retries incomplete publications), so listeners must be idempotent.
 */
public record UserRegistered(UserId userId, String fullName, String email, Instant registeredAt) {
}
