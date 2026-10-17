package com.acme.blueprint.notifications.application;

import com.acme.blueprint.users.UserId;
import com.acme.blueprint.shared.BusinessRuleViolationException;

/**
 * The request names a user that does not exist. A 422, not a 404: the
 * addressed resource (the notifications collection) exists — it is the
 * request body that refers to something invalid.
 */
public class UnknownUserException extends BusinessRuleViolationException {

    UnknownUserException(UserId userId) {
        super("notifications.unknown-user", "User %s does not exist".formatted(userId));
    }
}
