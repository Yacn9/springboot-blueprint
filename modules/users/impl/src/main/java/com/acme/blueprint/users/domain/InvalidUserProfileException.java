package com.acme.blueprint.users.domain;

import com.acme.blueprint.shared.BusinessRuleViolationException;

public class InvalidUserProfileException extends BusinessRuleViolationException {

    InvalidUserProfileException(String field, String reason) {
        super("users.invalid-profile", "User profile field '%s' %s".formatted(field, reason));
    }
}
