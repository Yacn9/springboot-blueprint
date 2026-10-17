package com.acme.blueprint.users.application;

import java.util.Optional;

import com.acme.blueprint.users.UserDirectory;
import com.acme.blueprint.users.UserId;
import com.acme.blueprint.users.UserSummary;
import com.acme.blueprint.users.domain.User;
import com.acme.blueprint.users.domain.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements the module's published {@link UserDirectory} contract. This
 * is the only place the aggregate is translated into the API's
 * {@link UserSummary}, so the internal model can change freely as long
 * as this mapping still holds.
 */
@Service
@Transactional(readOnly = true)
class UserDirectoryService implements UserDirectory {

    private final UserRepository users;

    UserDirectoryService(UserRepository users) {
        this.users = users;
    }

    @Override
    public Optional<UserSummary> findById(UserId id) {
        return users.findById(id).map(UserDirectoryService::toSummary);
    }

    private static UserSummary toSummary(User user) {
        var profile = user.profile();
        return new UserSummary(user.id(), profile.fullName(), profile.email(), profile.mobile());
    }
}
