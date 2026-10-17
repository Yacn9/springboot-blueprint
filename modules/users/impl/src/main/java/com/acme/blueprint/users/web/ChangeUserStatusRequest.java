package com.acme.blueprint.users.web;

import com.acme.blueprint.users.domain.UserStatus;

import jakarta.validation.constraints.NotNull;

/** Body of {@code PUT /api/users/{userId}/status}. */
record ChangeUserStatusRequest(@NotNull UserStatus status) {
}
