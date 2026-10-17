package com.acme.blueprint.users.web;

import java.time.Instant;
import java.util.UUID;

import com.acme.blueprint.users.domain.User;
import com.acme.blueprint.users.domain.UserStatus;

/** A user as the REST API presents it. */
record UserResponse(
        UUID id,
        String nationalCode,
        UserStatus status,
        UserProfileDto profile,
        Instant createdAt,
        Instant updatedAt) {

    static UserResponse from(User user) {
        return new UserResponse(
                user.id().value(),
                user.nationalCode().value(),
                user.status(),
                UserProfileDto.from(user.profile()),
                user.createdAt(),
                user.updatedAt());
    }
}
