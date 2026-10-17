package com.acme.blueprint.users;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Typed user identifier. Modules exchange this instead of a bare
 * {@code UUID}, so a notification id can never be passed where a user id
 * is expected.
 */
public record UserId(UUID value) implements Serializable {

    public UserId {
        Objects.requireNonNull(value, "value");
    }

    public static UserId newId() {
        return new UserId(UUID.randomUUID());
    }

    /** Also the factory Spring MVC uses to bind {@code {userId}} path variables. */
    public static UserId of(String value) {
        return new UserId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
