package com.acme.blueprint.customers.web;

import com.acme.blueprint.customers.domain.NationalCode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Body of {@code POST /api/customers}. The national code is the customer's
 * immutable identity; everything editable sits in {@code profile}. The format
 * is checked here (400); the checksum by {@link NationalCode} (422).
 */
record RegisterCustomerRequest(
        @NotNull @Pattern(regexp = NationalCode.FORMAT, message = "must be exactly 10 digits") String nationalCode,
        @NotNull @Valid CustomerProfileDto profile) {
}
