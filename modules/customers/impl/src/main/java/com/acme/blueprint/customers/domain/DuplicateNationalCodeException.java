package com.acme.blueprint.customers.domain;

import com.acme.blueprint.shared.ConflictException;

/**
 * National codes are unique across customers. Thrown by the use case's
 * up-front check and by {@link CustomerRepository#add} when a concurrent
 * registration wins the race to the unique constraint.
 */
public class DuplicateNationalCodeException extends ConflictException {

    public DuplicateNationalCodeException(NationalCode nationalCode) {
        super("customers.duplicate-national-code",
                "A customer with national code %s is already registered".formatted(nationalCode.value()));
    }
}
