package com.acme.blueprint.users.application;

import com.acme.blueprint.users.UserId;
import com.acme.blueprint.shared.ResourceNotFoundException;

public class UserNotFoundException extends ResourceNotFoundException {

    UserNotFoundException(UserId id) {
        super("users.not-found", "User %s does not exist".formatted(id));
    }
}
