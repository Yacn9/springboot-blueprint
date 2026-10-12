package com.acme.blueprint.customers.application;

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

import com.acme.blueprint.customers.CustomerId;
import com.acme.blueprint.customers.CustomerRegistered;
import com.acme.blueprint.customers.domain.Customer;
import com.acme.blueprint.customers.domain.CustomerProfile;
import com.acme.blueprint.customers.domain.CustomerRepository;
import com.acme.blueprint.customers.domain.CustomerStatus;
import com.acme.blueprint.customers.domain.CustomerTier;
import com.acme.blueprint.customers.domain.DuplicateNationalCodeException;
import com.acme.blueprint.customers.domain.NationalCode;

import org.junit.jupiter.api.Test;

/**
 * The use cases with hand-written fakes for their ports — no Spring, no
 * database, no mocking library. This is what the ports buy: orchestration and
 * the error codes clients depend on are tested in milliseconds. A fake must
 * honour its port's contract, or the test proves less than it claims.
 */
class CustomerManagementTest {

    private static final Instant NOW = Instant.parse("2026-10-17T08:00:00Z");

    private final InMemoryCustomers repository = new InMemoryCustomers();
    private final List<Object> published = new ArrayList<>();
    private final CustomerFinder unusedFinder = (criteria, pageable) -> {
        throw new UnsupportedOperationException();
    };
    private final CustomerManagement customers = new CustomerManagement(
            repository, unusedFinder, published::add, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void registersAPendingCustomerAndPublishesTheEvent() {
        Customer customer = customers.register(NationalCode.of("0012345679"), profile());

        assertThat(customer.status()).isEqualTo(CustomerStatus.PENDING);
        assertThat(published).singleElement().isEqualTo(
                new CustomerRegistered(customer.id(), "Ali Rezaei", "ali@example.com", NOW));
    }

    @Test
    void rejectsADuplicateNationalCodeWithoutPublishing() {
        customers.register(NationalCode.of("0012345679"), profile());
        published.clear();

        assertCode(() -> customers.register(NationalCode.of("0012345679"), profile()), "customers.duplicate-national-code");
        assertThat(published).isEmpty();
    }

    /** The up-front check said "free", but a concurrent registration won: add() refuses, nothing is published. */
    @Test
    void publishesNothingWhenARaceIsLostAtTheConstraint() {
        repository.add(Customer.register(NationalCode.of("0012345679"), profile(), NOW));
        repository.answerExistenceChecksWithStaleFalse();

        assertCode(() -> customers.register(NationalCode.of("0012345679"), profile()), "customers.duplicate-national-code");
        assertThat(published).isEmpty();
    }

    @Test
    void reportsAnUnknownCustomer() {
        assertCode(() -> customers.get(CustomerId.newId()), "customers.not-found");
    }

    @Test
    void refusesAnUpdateBasedOnAStaleVersion() {
        CustomerId id = customers.register(NationalCode.of("0012345679"), profile()).id();

        assertCode(() -> customers.updateProfile(id, 7, profile()), "request.version-mismatch");
    }

    @Test
    void refusesToEditAClosedCustomer() {
        Customer customer = customers.register(NationalCode.of("0012345679"), profile());
        customers.changeStatus(customer.id(), customer.version(), CustomerStatus.CLOSED);

        assertCode(() -> customers.updateProfile(customer.id(), customer.version(), profile()), "customers.closed");
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).extracting("code").isEqualTo(code);
    }

    private static CustomerProfile profile() {
        return new CustomerProfile("Ali", "Rezaei", "09121234567", "ali@example.com", CustomerTier.GOLD, "Tehran Central", null);
    }

    /** Keeps {@link CustomerRepository}'s contract: {@code add} refuses a taken national code. */
    private static final class InMemoryCustomers implements CustomerRepository {

        private final Map<CustomerId, Customer> byId = new HashMap<>();
        private boolean staleExistenceChecks;

        void answerExistenceChecksWithStaleFalse() {
            staleExistenceChecks = true;
        }

        @Override
        public Customer add(Customer customer) {
            if (holds(customer.nationalCode())) {
                throw new DuplicateNationalCodeException(customer.nationalCode());
            }
            byId.put(customer.id(), customer);
            return customer;
        }

        @Override
        public Optional<Customer> findById(CustomerId id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public boolean existsByNationalCode(NationalCode nationalCode) {
            return !staleExistenceChecks && holds(nationalCode);
        }

        private boolean holds(NationalCode nationalCode) {
            return byId.values().stream().anyMatch(customer -> customer.nationalCode().equals(nationalCode));
        }
    }
}
