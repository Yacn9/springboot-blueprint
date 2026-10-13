package com.acme.blueprint.customers.infrastructure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.sql.SQLException;
import java.time.Instant;

import com.acme.blueprint.customers.domain.Customer;
import com.acme.blueprint.customers.domain.CustomerProfile;
import com.acme.blueprint.customers.domain.CustomerTier;
import com.acme.blueprint.customers.domain.DuplicateNationalCodeException;
import com.acme.blueprint.customers.domain.NationalCode;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Which violations the adapter turns into business exceptions. How each
 * database spells a constraint name is {@code ConstraintViolationsTest}'s
 * concern in :shared; the module test exercises the real constraint.
 */
class CustomerPersistenceAdapterTest {

    private final SpringDataCustomerRepository jpa = mock(SpringDataCustomerRepository.class);
    private final CustomerPersistenceAdapter adapter = new CustomerPersistenceAdapter(jpa);

    @Test
    void translatesANationalCodeViolationToADuplicate() {
        givenSaveViolates("customer_national_code_uk");

        assertThatThrownBy(() -> adapter.add(customer())).isInstanceOf(DuplicateNationalCodeException.class);
    }

    @Test
    void rethrowsAnyOtherViolation() {
        givenSaveViolates("customer_pkey");

        assertThatThrownBy(() -> adapter.add(customer())).isInstanceOf(DataIntegrityViolationException.class);
    }

    private void givenSaveViolates(String constraintName) {
        var violation = new ConstraintViolationException("violation", new SQLException("violation"), constraintName);
        given(jpa.saveAndFlush(any())).willThrow(new DataIntegrityViolationException("violation", violation));
    }

    private static Customer customer() {
        return Customer.register(NationalCode.of("0012345679"),
                new CustomerProfile("Ali", "Rezaei", "09121234567", "ali@example.com", CustomerTier.GOLD, "Tehran Central", null),
                Instant.parse("2026-10-17T08:00:00Z"));
    }
}
