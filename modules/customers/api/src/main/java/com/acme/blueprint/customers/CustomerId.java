package com.acme.blueprint.customers;

import java.util.Objects;
import java.util.UUID;

/**
 * Typed customer identifier. Modules exchange this instead of a bare
 * {@code UUID}, so a notification id can never be passed where a customer id
 * is expected.
 */
public record CustomerId(UUID value) {

    public CustomerId {
        Objects.requireNonNull(value, "value");
    }

    public static CustomerId newId() {
        return new CustomerId(UUID.randomUUID());
    }

    /** Also the factory Spring MVC uses to bind {@code {customerId}} path variables. */
    public static CustomerId of(String value) {
        return new CustomerId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
