package com.acme.blueprint.users.domain;

import com.acme.blueprint.shared.BusinessRuleViolationException;

public class InvalidNationalCodeException extends BusinessRuleViolationException {

    InvalidNationalCodeException(String value) {
        super("users.invalid-national-code", "'%s' is not a valid national code".formatted(value));
    }
}
