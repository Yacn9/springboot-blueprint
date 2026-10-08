package com.acme.blueprint.customers.domain;

import java.util.Optional;

import com.acme.blueprint.customers.CustomerId;

/**
 * Persistence port for the aggregate, phrased in domain types. The JPA
 * adapter in {@code infrastructure} implements it; the domain never sees
 * Spring Data.
 *
 * <p>Changes to a customer obtained from {@link #findById} are written when
 * the surrounding transaction commits (unit of work); there is no
 * {@code update} method to forget to call.
 */
public interface CustomerRepository {

    /**
     * Stores a new customer and writes it immediately, so uniqueness is
     * enforced here — before the caller publishes events or causes other
     * side effects — rather than at commit.
     *
     * @throws DuplicateNationalCodeException if the national code is taken,
     *         including by a registration that committed concurrently
     */
    Customer add(Customer customer);

    Optional<Customer> findById(CustomerId id);

    boolean existsByNationalCode(NationalCode nationalCode);
}
