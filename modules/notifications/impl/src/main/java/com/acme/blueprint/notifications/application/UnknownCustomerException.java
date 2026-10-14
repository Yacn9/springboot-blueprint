package com.acme.blueprint.notifications.application;

import com.acme.blueprint.customers.CustomerId;
import com.acme.blueprint.shared.BusinessRuleViolationException;

/**
 * The request names a customer that does not exist. A 422, not a 404: the
 * addressed resource (the notifications collection) exists — it is the
 * request body that refers to something invalid.
 */
public class UnknownCustomerException extends BusinessRuleViolationException {

    UnknownCustomerException(CustomerId customerId) {
        super("notifications.unknown-customer", "Customer %s does not exist".formatted(customerId));
    }
}
