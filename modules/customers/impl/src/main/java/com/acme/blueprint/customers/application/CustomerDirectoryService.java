package com.acme.blueprint.customers.application;

import java.util.Optional;

import com.acme.blueprint.customers.CustomerDirectory;
import com.acme.blueprint.customers.CustomerId;
import com.acme.blueprint.customers.CustomerSummary;
import com.acme.blueprint.customers.domain.Customer;
import com.acme.blueprint.customers.domain.CustomerRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements the module's published {@link CustomerDirectory} contract. This
 * is the only place the aggregate is translated into the API's
 * {@link CustomerSummary}, so the internal model can change freely as long
 * as this mapping still holds.
 */
@Service
@Transactional(readOnly = true)
class CustomerDirectoryService implements CustomerDirectory {

    private final CustomerRepository customers;

    CustomerDirectoryService(CustomerRepository customers) {
        this.customers = customers;
    }

    @Override
    public Optional<CustomerSummary> findById(CustomerId id) {
        return customers.findById(id).map(CustomerDirectoryService::toSummary);
    }

    private static CustomerSummary toSummary(Customer customer) {
        var profile = customer.profile();
        return new CustomerSummary(customer.id(), profile.fullName(), profile.email(), profile.mobile());
    }
}
