package com.acme.blueprint.users.domain;

import java.util.Optional;

import com.acme.blueprint.users.UserId;

/**
 * Persistence port for the aggregate, phrased in domain types. The JPA
 * adapter in {@code infrastructure} implements it; the domain never sees
 * Spring Data.
 *
 * <p>Changes to a user obtained from {@link #findById} are written when
 * the surrounding transaction commits (unit of work); there is no
 * {@code update} method to forget to call.
 */
public interface UserRepository {

    /**
     * Stores a new user and writes it immediately, so uniqueness is
     * enforced here — before the caller publishes events or causes other
     * side effects — rather than at commit.
     *
     * @throws DuplicateNationalCodeException if the national code is taken,
     *         including by a registration that committed concurrently
     */
    User add(User user);

    Optional<User> findById(UserId id);

    boolean existsByNationalCode(NationalCode nationalCode);
}
