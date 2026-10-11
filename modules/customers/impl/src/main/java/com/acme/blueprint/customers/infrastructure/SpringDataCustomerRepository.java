package com.acme.blueprint.customers.infrastructure;

import java.util.UUID;

import com.acme.blueprint.customers.domain.Customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Spring Data's view of the customer table. Package-private: only the adapter uses it. */
interface SpringDataCustomerRepository extends JpaRepository<Customer, UUID>, JpaSpecificationExecutor<Customer> {

    boolean existsByNationalCode(String nationalCode);
}
