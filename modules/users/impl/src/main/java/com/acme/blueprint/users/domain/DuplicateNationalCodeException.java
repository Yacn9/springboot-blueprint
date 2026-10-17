package com.acme.blueprint.users.domain;

import com.acme.blueprint.shared.ConflictException;

/**
 * National codes are unique across users. Thrown by the use case's
 * up-front check and by {@link UserRepository#add} when a concurrent
 * registration wins the race to the unique constraint.
 */
public class DuplicateNationalCodeException extends ConflictException {

    public DuplicateNationalCodeException(NationalCode nationalCode) {
        super("users.duplicate-national-code",
                "A user with national code %s is already registered".formatted(nationalCode.value()));
    }
}
