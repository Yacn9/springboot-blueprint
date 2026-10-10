package com.acme.blueprint.customers.application;

import com.acme.blueprint.customers.CustomerId;
import com.acme.blueprint.shared.ResourceNotFoundException;

public class CustomerNotFoundException extends ResourceNotFoundException {

    CustomerNotFoundException(CustomerId id) {
        super("customers.not-found", "Customer %s does not exist".formatted(id));
    }
}
