package com.acme.blueprint.customers.domain;

/**
 * Lifecycle state of a customer; the allowed transitions are encoded in
 * {@link Customer#changeStatus}. Internal on purpose: when another module
 * needs lifecycle information, publish a purpose-built projection in
 * customers-api rather than this enum, so the state machine can keep evolving.
 */
public enum CustomerStatus {
    PENDING,
    ACTIVE,
    SUSPENDED,
    CLOSED
}
