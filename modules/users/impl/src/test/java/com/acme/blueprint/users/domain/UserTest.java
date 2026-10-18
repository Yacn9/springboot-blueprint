package com.acme.blueprint.users.domain;

import static com.acme.blueprint.users.domain.UserStatus.ACTIVE;
import static com.acme.blueprint.users.domain.UserStatus.CLOSED;
import static com.acme.blueprint.users.domain.UserStatus.PENDING;
import static com.acme.blueprint.users.domain.UserStatus.SUSPENDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/** Plain unit tests: the domain needs no Spring context and no database. */
class UserTest {

    private static final Instant REGISTERED_AT = Instant.parse("2026-10-17T08:00:00Z");
    private static final Instant LATER = REGISTERED_AT.plusSeconds(60);

    @Test
    void startsPendingWhenRegistered() {
        User user = newUser();

        assertThat(user.status()).isEqualTo(PENDING);
        assertThat(user.createdAt()).isEqualTo(REGISTERED_AT);
        assertThat(user.updatedAt()).isEqualTo(REGISTERED_AT);
    }

    @Test
    void followsTheAllowedLifecycle() {
        User user = newUser();

        user.changeStatus(ACTIVE, LATER);
        user.changeStatus(SUSPENDED, LATER);
        user.changeStatus(ACTIVE, LATER);
        user.changeStatus(CLOSED, LATER);

        assertThat(user.status()).isEqualTo(CLOSED);
        assertThat(user.updatedAt()).isEqualTo(LATER);
    }

    @Test
    void rejectsAForbiddenTransition() {
        User user = newUser();

        assertThatThrownBy(() -> user.changeStatus(SUSPENDED, LATER))
                .isInstanceOf(UserStatusTransitionException.class);
        assertThat(user.status()).isEqualTo(PENDING);
    }

    @Test
    void treatsTheCurrentStatusAsANoOp() {
        User user = newUser();

        user.changeStatus(PENDING, LATER);

        assertThat(user.updatedAt()).isEqualTo(REGISTERED_AT);
    }

    @Test
    void isTerminalOnceClosed() {
        User user = newUser();
        user.changeStatus(CLOSED, LATER);

        assertThatThrownBy(() -> user.changeStatus(ACTIVE, LATER))
                .isInstanceOf(UserStatusTransitionException.class);
        assertThatThrownBy(() -> user.updateProfile(profile("Sara"), LATER))
                .isInstanceOf(UserClosedException.class);
    }

    @Test
    void replacesTheWholeProfileOnUpdate() {
        User user = newUser();

        user.updateProfile(profile("Sara"), LATER);

        assertThat(user.profile().firstName()).isEqualTo("Sara");
        assertThat(user.updatedAt()).isEqualTo(LATER);
    }

    private static User newUser() {
        return User.register(NationalCode.of("0012345679"), profile("Vesen"), REGISTERED_AT);
    }

    private static UserProfile profile(String firstName) {
        return new UserProfile(
                firstName, "Naqibi", "09121234567", "vesen@example.com", UserTier.GOLD, "Tehran Central", null);
    }
}
