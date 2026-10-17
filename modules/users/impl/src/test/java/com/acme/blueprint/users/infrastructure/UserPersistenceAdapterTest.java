package com.acme.blueprint.users.infrastructure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.sql.SQLException;
import java.time.Instant;

import com.acme.blueprint.users.domain.User;
import com.acme.blueprint.users.domain.UserProfile;
import com.acme.blueprint.users.domain.UserTier;
import com.acme.blueprint.users.domain.DuplicateNationalCodeException;
import com.acme.blueprint.users.domain.NationalCode;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Which violations the adapter turns into business exceptions. How each
 * database spells a constraint name is {@code ConstraintViolationsTest}'s
 * concern in :shared; the module test exercises the real constraint.
 */
class UserPersistenceAdapterTest {

    private final SpringDataUserRepository jpa = mock(SpringDataUserRepository.class);
    private final UserPersistenceAdapter adapter = new UserPersistenceAdapter(jpa);

    @Test
    void translatesANationalCodeViolationToADuplicate() {
        givenSaveViolates("user_national_code_uk");

        assertThatThrownBy(() -> adapter.add(user())).isInstanceOf(DuplicateNationalCodeException.class);
    }

    @Test
    void rethrowsAnyOtherViolation() {
        givenSaveViolates("user_pkey");

        assertThatThrownBy(() -> adapter.add(user())).isInstanceOf(DataIntegrityViolationException.class);
    }

    private void givenSaveViolates(String constraintName) {
        var violation = new ConstraintViolationException("violation", new SQLException("violation"), constraintName);
        given(jpa.saveAndFlush(any())).willThrow(new DataIntegrityViolationException("violation", violation));
    }

    private static User user() {
        return User.register(NationalCode.of("0012345679"),
                new UserProfile("Vesen", "Naqibi", "09121234567", "vesen@example.com", UserTier.GOLD, "Tehran Central", null),
                Instant.parse("2026-10-17T08:00:00Z"));
    }
}
