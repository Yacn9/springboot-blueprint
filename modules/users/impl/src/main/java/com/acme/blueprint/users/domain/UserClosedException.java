package com.acme.blueprint.users.domain;

import com.acme.blueprint.users.UserId;
import com.acme.blueprint.shared.BusinessRuleViolationException;

public class UserClosedException extends BusinessRuleViolationException {

    UserClosedException(UserId id) {
        super("users.closed", "User %s is closed and can no longer be changed".formatted(id));
    }
}
