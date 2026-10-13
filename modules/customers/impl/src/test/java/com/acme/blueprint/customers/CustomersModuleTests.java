package com.acme.blueprint.customers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Comparator;

import com.acme.blueprint.customers.application.CustomerFinder;
import com.acme.blueprint.customers.application.CustomerManagement;
import com.acme.blueprint.customers.application.CustomerSearchCriteria;
import com.acme.blueprint.customers.domain.Customer;
import com.acme.blueprint.customers.domain.CustomerProfile;
import com.acme.blueprint.customers.domain.CustomerRepository;
import com.acme.blueprint.customers.domain.CustomerStatus;
import com.acme.blueprint.customers.domain.CustomerTier;
import com.acme.blueprint.customers.domain.DuplicateNationalCodeException;
import com.acme.blueprint.customers.domain.NationalCode;
import com.acme.blueprint.shared.InvalidSortException;
import com.acme.blueprint.shared.testing.DatabaseTestcontainers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;

/**
 * Boots the customers module alone (plus :shared) against a real database,
 * with the production platform settings — Flyway migrations, Hibernate schema
 * validation and the published event are all exercised for real. No other
 * module is on the classpath.
 */
@ApplicationModuleTest
@Import(DatabaseTestcontainers.class)
class CustomersModuleTests {

    @Autowired
    CustomerManagement customers;

    @Autowired
    CustomerRepository repository;

    @Autowired
    CustomerDirectory directory;

    @Test
    void publishesCustomerRegisteredWhenACustomerIsRegistered(Scenario scenario) {
        scenario.stimulate(() -> customers.register(NationalCode.of("0012345679"), profile("ali@example.com")))
                .andWaitForEventOfType(CustomerRegistered.class)
                .toArriveAndVerify(event -> {
                    assertThat(event.fullName()).isEqualTo("Ali Rezaei");
                    assertThat(event.email()).isEqualTo("ali@example.com");
                });
    }

    @Test
    void rejectsASecondCustomerWithTheSameNationalCode() {
        customers.register(NationalCode.of("1234567891"), profile("first@example.com"));

        assertThatThrownBy(() -> customers.register(NationalCode.of("1234567891"), profile("second@example.com")))
                .isInstanceOf(DuplicateNationalCodeException.class);
    }

    /** The race the up-front check cannot see: the unique constraint is translated, not leaked as a 500. */
    @Test
    void translatesTheUniqueConstraintIntoADuplicateNationalCode() {
        NationalCode code = NationalCode.of("0076229742");
        repository.add(Customer.register(code, profile("race-1@example.com"), Instant.now()));

        assertThatThrownBy(() -> repository.add(Customer.register(code, profile("race-2@example.com"), Instant.now())))
                .isInstanceOf(DuplicateNationalCodeException.class);
    }

    @Test
    void incrementsTheVersionOnEveryCommittedChange() {
        Customer registered = customers.register(NationalCode.of("0499370899"), profile("dir@example.com"));

        Customer activated = customers.changeStatus(registered.id(), registered.version(), CustomerStatus.ACTIVE);

        assertThat(activated.version()).isEqualTo(registered.version() + 1);
        assertThat(directory.findById(registered.id()))
                .hasValueSatisfying(summary -> assertThat(summary.email()).isEqualTo("dir@example.com"));
    }

    @Test
    void searchesAcrossNameEmailAndTier() {
        customers.register(NationalCode.of("0067749828"), profile("search-me@example.com"));

        var byEmail = customers.search(new CustomerSearchCriteria("SEARCH-ME", null, null), Pageable.ofSize(10));
        var byOtherTier = customers.search(new CustomerSearchCriteria("search-me", null, CustomerTier.BASIC), Pageable.ofSize(10));

        assertThat(byEmail.getContent()).extracting(c -> c.profile().email()).containsExactly("search-me@example.com");
        assertThat(byOtherTier.getContent()).isEmpty();
    }

    /** Runs every public sort key and the status filter against the real schema, so a renamed field fails here. */
    @Test
    void sortsByEveryPublicKeyAndFiltersByStatus() {
        customers.register(NationalCode.of("2000000002"), profile("Zand", "zand@example.com"));
        customers.register(NationalCode.of("3000000003"), profile("Abbasi", "abbasi@example.com"));
        var pending = new CustomerSearchCriteria("@example.com", CustomerStatus.PENDING, null);

        var byLastName = customers.search(pending, PageRequest.of(0, 100, Sort.by(CustomerFinder.SORT_BY_LAST_NAME)));
        var newestFirst = customers.search(pending, PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, CustomerFinder.SORT_BY_CREATED_AT)));
        var active = customers.search(new CustomerSearchCriteria("zand@", CustomerStatus.ACTIVE, null), Pageable.ofSize(10));

        assertThat(byLastName.getContent()).extracting(c -> c.profile().lastName()).isSorted();
        assertThat(newestFirst.getContent()).extracting(Customer::createdAt).isSortedAccordingTo(java.util.Comparator.reverseOrder());
        assertThat(active.getContent()).isEmpty();
    }

    @Test
    void rejectsASortKeyThatIsNotPublic() {
        assertThatThrownBy(() -> customers.search(
                new CustomerSearchCriteria(null, null, null), PageRequest.of(0, 10, Sort.by("version"))))
                .isInstanceOf(InvalidSortException.class);
    }

    private static CustomerProfile profile(String email) {
        return profile("Rezaei", email);
    }

    private static CustomerProfile profile(String lastName, String email) {
        return new CustomerProfile("Ali", lastName, "09121234567", email, CustomerTier.GOLD, "Tehran Central", null);
    }
}
