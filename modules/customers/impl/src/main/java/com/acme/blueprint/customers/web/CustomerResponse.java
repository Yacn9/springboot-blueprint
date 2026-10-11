package com.acme.blueprint.customers.web;

import java.time.Instant;
import java.util.UUID;

import com.acme.blueprint.customers.domain.Customer;
import com.acme.blueprint.customers.domain.CustomerStatus;

/** A customer as the REST API presents it. */
record CustomerResponse(
        UUID id,
        String nationalCode,
        CustomerStatus status,
        CustomerProfileDto profile,
        Instant createdAt,
        Instant updatedAt) {

    static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
                customer.id().value(),
                customer.nationalCode().value(),
                customer.status(),
                CustomerProfileDto.from(customer.profile()),
                customer.createdAt(),
                customer.updatedAt());
    }
}
