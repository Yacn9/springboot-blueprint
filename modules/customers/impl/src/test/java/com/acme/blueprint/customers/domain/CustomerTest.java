package com.acme.blueprint.customers.domain;

import static com.acme.blueprint.customers.domain.CustomerStatus.ACTIVE;
import static com.acme.blueprint.customers.domain.CustomerStatus.CLOSED;
import static com.acme.blueprint.customers.domain.CustomerStatus.PENDING;
import static com.acme.blueprint.customers.domain.CustomerStatus.SUSPENDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/** Plain unit tests: the domain needs no Spring context and no database. */
class CustomerTest {

    private static final Instant REGISTERED_AT = Instant.parse("2026-10-17T08:00:00Z");
    private static final Instant LATER = REGISTERED_AT.plusSeconds(60);

    @Test
    void startsPendingWhenRegistered() {
        Customer customer = newCustomer();

        assertThat(customer.status()).isEqualTo(PENDING);
        assertThat(customer.createdAt()).isEqualTo(REGISTERED_AT);
        assertThat(customer.updatedAt()).isEqualTo(REGISTERED_AT);
    }

    @Test
    void followsTheAllowedLifecycle() {
        Customer customer = newCustomer();

        customer.changeStatus(ACTIVE, LATER);
        customer.changeStatus(SUSPENDED, LATER);
        customer.changeStatus(ACTIVE, LATER);
        customer.changeStatus(CLOSED, LATER);

        assertThat(customer.status()).isEqualTo(CLOSED);
        assertThat(customer.updatedAt()).isEqualTo(LATER);
    }

    @Test
    void rejectsAForbiddenTransition() {
        Customer customer = newCustomer();

        assertThatThrownBy(() -> customer.changeStatus(SUSPENDED, LATER))
                .isInstanceOf(CustomerStatusTransitionException.class);
        assertThat(customer.status()).isEqualTo(PENDING);
    }

    @Test
    void treatsTheCurrentStatusAsANoOp() {
        Customer customer = newCustomer();

        customer.changeStatus(PENDING, LATER);

        assertThat(customer.updatedAt()).isEqualTo(REGISTERED_AT);
    }

    @Test
    void isTerminalOnceClosed() {
        Customer customer = newCustomer();
        customer.changeStatus(CLOSED, LATER);

        assertThatThrownBy(() -> customer.changeStatus(ACTIVE, LATER))
                .isInstanceOf(CustomerStatusTransitionException.class);
        assertThatThrownBy(() -> customer.updateProfile(profile("Sara"), LATER))
                .isInstanceOf(CustomerClosedException.class);
    }

    @Test
    void replacesTheWholeProfileOnUpdate() {
        Customer customer = newCustomer();

        customer.updateProfile(profile("Sara"), LATER);

        assertThat(customer.profile().firstName()).isEqualTo("Sara");
        assertThat(customer.updatedAt()).isEqualTo(LATER);
    }

    private static Customer newCustomer() {
        return Customer.register(NationalCode.of("0012345679"), profile("Ali"), REGISTERED_AT);
    }

    private static CustomerProfile profile(String firstName) {
        return new CustomerProfile(
                firstName, "Rezaei", "09121234567", "ali@example.com", CustomerTier.GOLD, "Tehran Central", null);
    }
}
