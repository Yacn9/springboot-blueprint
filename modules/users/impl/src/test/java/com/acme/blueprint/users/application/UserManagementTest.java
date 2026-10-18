package com.acme.blueprint.users.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.acme.blueprint.users.UserId;
import com.acme.blueprint.users.UserRegistered;
import com.acme.blueprint.users.domain.User;
import com.acme.blueprint.users.domain.UserProfile;
import com.acme.blueprint.users.domain.UserRepository;
import com.acme.blueprint.users.domain.UserStatus;
import com.acme.blueprint.users.domain.UserTier;
import com.acme.blueprint.users.domain.DuplicateNationalCodeException;
import com.acme.blueprint.users.domain.NationalCode;

import org.junit.jupiter.api.Test;

/**
 * The use cases with hand-written fakes for their ports — no Spring, no
 * database, no mocking library. This is what the ports buy: orchestration and
 * the error codes clients depend on are tested in milliseconds. A fake must
 * honour its port's contract, or the test proves less than it claims.
 */
class UserManagementTest {

    private static final Instant NOW = Instant.parse("2026-10-17T08:00:00Z");

    private final InMemoryUsers repository = new InMemoryUsers();
    private final List<Object> published = new ArrayList<>();
    private final UserFinder unusedFinder = (criteria, pageable) -> {
        throw new UnsupportedOperationException();
    };
    private final UserManagement users = new UserManagement(
            repository, unusedFinder, published::add, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void registersAPendingUserAndPublishesTheEvent() {
        User user = users.register(NationalCode.of("0012345679"), profile());

        assertThat(user.status()).isEqualTo(UserStatus.PENDING);
        assertThat(published).singleElement().isEqualTo(
                new UserRegistered(user.id(), "Vesen Naqibi", "vesen@example.com", NOW));
    }

    @Test
    void rejectsADuplicateNationalCodeWithoutPublishing() {
        users.register(NationalCode.of("0012345679"), profile());
        published.clear();

        assertCode(() -> users.register(NationalCode.of("0012345679"), profile()), "users.duplicate-national-code");
        assertThat(published).isEmpty();
    }

    /** The up-front check said "free", but a concurrent registration won: add() refuses, nothing is published. */
    @Test
    void publishesNothingWhenARaceIsLostAtTheConstraint() {
        repository.add(User.register(NationalCode.of("0012345679"), profile(), NOW));
        repository.answerExistenceChecksWithStaleFalse();

        assertCode(() -> users.register(NationalCode.of("0012345679"), profile()), "users.duplicate-national-code");
        assertThat(published).isEmpty();
    }

    @Test
    void reportsAnUnknownUser() {
        assertCode(() -> users.get(UserId.newId()), "users.not-found");
    }

    @Test
    void refusesAnUpdateBasedOnAStaleVersion() {
        UserId id = users.register(NationalCode.of("0012345679"), profile()).id();

        assertCode(() -> users.updateProfile(id, 7, profile()), "request.version-mismatch");
    }

    @Test
    void refusesToEditAClosedUser() {
        User user = users.register(NationalCode.of("0012345679"), profile());
        users.changeStatus(user.id(), user.version(), UserStatus.CLOSED);

        assertCode(() -> users.updateProfile(user.id(), user.version(), profile()), "users.closed");
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).extracting("code").isEqualTo(code);
    }

    private static UserProfile profile() {
        return new UserProfile("Vesen", "Naqibi", "09121234567", "vesen@example.com", UserTier.GOLD, "Tehran Central", null);
    }

    /** Keeps {@link UserRepository}'s contract: {@code add} refuses a taken national code. */
    private static final class InMemoryUsers implements UserRepository {

        private final Map<UserId, User> byId = new HashMap<>();
        private boolean staleExistenceChecks;

        void answerExistenceChecksWithStaleFalse() {
            staleExistenceChecks = true;
        }

        @Override
        public User add(User user) {
            if (holds(user.nationalCode())) {
                throw new DuplicateNationalCodeException(user.nationalCode());
            }
            byId.put(user.id(), user);
            return user;
        }

        @Override
        public Optional<User> findById(UserId id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public boolean existsByNationalCode(NationalCode nationalCode) {
            return !staleExistenceChecks && holds(nationalCode);
        }

        private boolean holds(NationalCode nationalCode) {
            return byId.values().stream().anyMatch(user -> user.nationalCode().equals(nationalCode));
        }
    }
}
