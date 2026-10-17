package com.acme.blueprint.users.web;

import com.acme.blueprint.users.domain.NationalCode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Body of {@code POST /api/users}. The national code is the user's
 * immutable identity; everything editable sits in {@code profile}. The format
 * is checked here (400); the checksum by {@link NationalCode} (422).
 */
record RegisterUserRequest(
        @NotNull @Pattern(regexp = NationalCode.FORMAT, message = "must be exactly 10 digits") String nationalCode,
        @NotNull @Valid UserProfileDto profile) {
}
