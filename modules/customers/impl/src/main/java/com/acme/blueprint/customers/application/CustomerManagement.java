package com.acme.blueprint.customers.application;

import java.time.Clock;

import com.acme.blueprint.customers.CustomerId;
import com.acme.blueprint.customers.CustomerRegistered;
import com.acme.blueprint.customers.domain.Customer;
import com.acme.blueprint.customers.domain.CustomerProfile;
import com.acme.blueprint.customers.domain.CustomerRepository;
import com.acme.blueprint.customers.domain.CustomerStatus;
import com.acme.blueprint.customers.domain.DuplicateNationalCodeException;
import com.acme.blueprint.customers.domain.NationalCode;
import com.acme.blueprint.shared.VersionMismatchException;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases of the customers module. Each public method is one transaction:
 * it loads the aggregate, asks it to change, and publishes what happened.
 * Business rules live in {@link Customer}; this class only orchestrates.
 */
@Service
@Transactional
public class CustomerManagement {

    private final CustomerRepository customers;
    private final CustomerFinder finder;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    CustomerManagement(CustomerRepository customers, CustomerFinder finder, ApplicationEventPublisher events, Clock clock) {
        this.customers = customers;
        this.finder = finder;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Registers a customer and publishes {@link CustomerRegistered}. The event
     * is written to the publication registry in this same transaction, so it
     * is delivered if and only if the customer is stored.
     */
    public Customer register(NationalCode nationalCode, CustomerProfile profile) {
        if (customers.existsByNationalCode(nationalCode)) {
            throw new DuplicateNationalCodeException(nationalCode); // the common case; add() covers the race
        }
        Customer customer = customers.add(Customer.register(nationalCode, profile, clock.instant()));
        events.publishEvent(new CustomerRegistered(
                customer.id(), profile.fullName(), profile.email(), customer.createdAt()));
        return customer;
    }

    /**
     * @param expectedVersion the version the caller last read; a different
     *        current version means someone else changed the customer since
     */
    public Customer updateProfile(CustomerId id, long expectedVersion, CustomerProfile profile) {
        Customer customer = loadAtVersion(id, expectedVersion);
        customer.updateProfile(profile, clock.instant());
        return customer;
    }

    /** @param expectedVersion see {@link #updateProfile} */
    public Customer changeStatus(CustomerId id, long expectedVersion, CustomerStatus status) {
        Customer customer = loadAtVersion(id, expectedVersion);
        customer.changeStatus(status, clock.instant());
        return customer;
    }

    @Transactional(readOnly = true)
    public Customer get(CustomerId id) {
        return load(id);
    }

    @Transactional(readOnly = true)
    public Page<Customer> search(CustomerSearchCriteria criteria, Pageable pageable) {
        return finder.search(criteria, pageable);
    }

    private Customer load(CustomerId id) {
        return customers.findById(id).orElseThrow(() -> new CustomerNotFoundException(id));
    }

    /**
     * Checks the caller's version inside the transaction; Hibernate's
     * {@code @Version} check at commit then covers the remaining window.
     */
    private Customer loadAtVersion(CustomerId id, long expectedVersion) {
        Customer customer = load(id);
        if (customer.version() != expectedVersion) {
            throw new VersionMismatchException("Customer", id, expectedVersion, customer.version());
        }
        return customer;
    }
}
