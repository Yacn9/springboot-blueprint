package com.acme.blueprint.customers.domain;

import com.acme.blueprint.shared.BusinessRuleViolationException;

public class InvalidCustomerProfileException extends BusinessRuleViolationException {

    InvalidCustomerProfileException(String field, String reason) {
        super("customers.invalid-profile", "Customer profile field '%s' %s".formatted(field, reason));
    }
}
