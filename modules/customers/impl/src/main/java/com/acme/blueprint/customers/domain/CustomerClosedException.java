package com.acme.blueprint.customers.domain;

import com.acme.blueprint.customers.CustomerId;
import com.acme.blueprint.shared.BusinessRuleViolationException;

public class CustomerClosedException extends BusinessRuleViolationException {

    CustomerClosedException(CustomerId id) {
        super("customers.closed", "Customer %s is closed and can no longer be changed".formatted(id));
    }
}
