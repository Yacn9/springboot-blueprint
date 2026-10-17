package com.acme.blueprint.users.application;

import java.time.Clock;

import com.acme.blueprint.users.UserId;
import com.acme.blueprint.users.UserRegistered;
import com.acme.blueprint.users.domain.User;
import com.acme.blueprint.users.domain.UserProfile;
import com.acme.blueprint.users.domain.UserRepository;
import com.acme.blueprint.users.domain.UserStatus;
import com.acme.blueprint.users.domain.DuplicateNationalCodeException;
import com.acme.blueprint.users.domain.NationalCode;
import com.acme.blueprint.shared.VersionMismatchException;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases of the users module. Each public method is one transaction:
 * it loads the aggregate, asks it to change, and publishes what happened.
 * Business rules live in {@link User}; this class only orchestrates.
 */
@Service
@Transactional
public class UserManagement {

    private final UserRepository users;
    private final UserFinder finder;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    UserManagement(UserRepository users, UserFinder finder, ApplicationEventPublisher events, Clock clock) {
        this.users = users;
        this.finder = finder;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Registers a user and publishes {@link UserRegistered}. The event
     * is written to the publication registry in this same transaction, so it
     * is delivered if and only if the user is stored.
     */
    public User register(NationalCode nationalCode, UserProfile profile) {
        if (users.existsByNationalCode(nationalCode)) {
            throw new DuplicateNationalCodeException(nationalCode); // the common case; add() covers the race
        }
        User user = users.add(User.register(nationalCode, profile, clock.instant()));
        events.publishEvent(new UserRegistered(
                user.id(), profile.fullName(), profile.email(), user.createdAt()));
        return user;
    }

    /**
     * @param expectedVersion the version the caller last read; a different
     *        current version means someone else changed the user since
     */
    public User updateProfile(UserId id, long expectedVersion, UserProfile profile) {
        User user = loadAtVersion(id, expectedVersion);
        user.updateProfile(profile, clock.instant());
        return user;
    }

    /** @param expectedVersion see {@link #updateProfile} */
    public User changeStatus(UserId id, long expectedVersion, UserStatus status) {
        User user = loadAtVersion(id, expectedVersion);
        user.changeStatus(status, clock.instant());
        return user;
    }

    @Transactional(readOnly = true)
    public User get(UserId id) {
        return load(id);
    }

    @Transactional(readOnly = true)
    public Page<User> search(UserSearchCriteria criteria, Pageable pageable) {
        return finder.search(criteria, pageable);
    }

    private User load(UserId id) {
        return users.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    /**
     * Checks the caller's version inside the transaction; Hibernate's
     * {@code @Version} check at commit then covers the remaining window.
     */
    private User loadAtVersion(UserId id, long expectedVersion) {
        User user = load(id);
        if (user.version() != expectedVersion) {
            throw new VersionMismatchException("User", id, expectedVersion, user.version());
        }
        return user;
    }
}
