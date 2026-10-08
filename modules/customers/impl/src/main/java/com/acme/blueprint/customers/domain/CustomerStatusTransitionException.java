package com.acme.blueprint.customers.domain;

import com.acme.blueprint.customers.CustomerId;
import com.acme.blueprint.shared.BusinessRuleViolationException;

public class CustomerStatusTransitionException extends BusinessRuleViolationException {

    CustomerStatusTransitionException(CustomerId id, CustomerStatus from, CustomerStatus to) {
        super("customers.status-transition-not-allowed",
                "Customer %s cannot move from %s to %s".formatted(id, from, to));
    }
}
