package com.acme.blueprint.users;

import java.util.Optional;

/**
 * Synchronous, read-only access to users for other modules.
 *
 * <p>Prefer reacting to {@link UserRegistered} when a module only needs to
 * know that something happened; call the directory when it needs current
 * data at the moment of a request.
 */
public interface UserDirectory {

    Optional<UserSummary> findById(UserId id);
}
