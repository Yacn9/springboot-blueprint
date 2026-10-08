package com.acme.blueprint.customers;

/**
 * The view of a customer other modules get. Deliberately narrower than the
 * aggregate: add a field here only when a consumer needs it, because every
 * field is a promise the customers module must keep.
 */
public record CustomerSummary(CustomerId id, String fullName, String email, String mobile) {
}
