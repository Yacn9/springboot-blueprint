package com.acme.blueprint.customers.application;

import com.acme.blueprint.customers.domain.Customer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Read-side port for list screens. Kept apart from
 * {@link com.acme.blueprint.customers.domain.CustomerRepository} because
 * paging and filtering are a query concern of the application, not a domain
 * invariant — and because it is phrased in Spring Data's paging types, which
 * the domain does not import.
 */
public interface CustomerFinder {

    /** The public sort keys; anything else is rejected. */
    String SORT_BY_CREATED_AT = "createdAt";
    String SORT_BY_LAST_NAME = "lastName";

    /**
     * @param pageable sorted by {@link #SORT_BY_CREATED_AT} and/or {@link #SORT_BY_LAST_NAME}
     * @throws com.acme.blueprint.shared.InvalidSortException for any other sort key
     */
    Page<Customer> search(CustomerSearchCriteria criteria, Pageable pageable);
}
