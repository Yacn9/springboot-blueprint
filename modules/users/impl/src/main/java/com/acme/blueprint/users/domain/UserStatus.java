package com.acme.blueprint.users.domain;

/**
 * Lifecycle state of a user; the allowed transitions are encoded in
 * {@link User#changeStatus}. Internal on purpose: when another module
 * needs lifecycle information, publish a purpose-built projection in
 * users-api rather than this enum, so the state machine can keep evolving.
 */
public enum UserStatus {
    PENDING,
    ACTIVE,
    SUSPENDED,
    CLOSED
}
