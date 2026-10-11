package com.acme.blueprint.customers.web;

import com.acme.blueprint.customers.domain.CustomerStatus;

import jakarta.validation.constraints.NotNull;

/** Body of {@code PUT /api/customers/{customerId}/status}. */
record ChangeCustomerStatusRequest(@NotNull CustomerStatus status) {
}
