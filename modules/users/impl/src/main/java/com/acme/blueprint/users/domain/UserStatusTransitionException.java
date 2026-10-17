package com.acme.blueprint.users.domain;

import com.acme.blueprint.users.UserId;
import com.acme.blueprint.shared.BusinessRuleViolationException;

public class UserStatusTransitionException extends BusinessRuleViolationException {

    UserStatusTransitionException(UserId id, UserStatus from, UserStatus to) {
        super("users.status-transition-not-allowed",
                "User %s cannot move from %s to %s".formatted(id, from, to));
    }
}
