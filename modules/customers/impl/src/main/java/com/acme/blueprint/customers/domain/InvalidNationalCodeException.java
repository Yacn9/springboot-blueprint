package com.acme.blueprint.customers.domain;

import com.acme.blueprint.shared.BusinessRuleViolationException;

public class InvalidNationalCodeException extends BusinessRuleViolationException {

    InvalidNationalCodeException(String value) {
        super("customers.invalid-national-code", "'%s' is not a valid national code".formatted(value));
    }
}
