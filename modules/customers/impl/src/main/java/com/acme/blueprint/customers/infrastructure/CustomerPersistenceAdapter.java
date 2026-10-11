package com.acme.blueprint.customers.infrastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.acme.blueprint.customers.CustomerId;
import com.acme.blueprint.customers.application.CustomerFinder;
import com.acme.blueprint.customers.application.CustomerSearchCriteria;
import com.acme.blueprint.customers.domain.Customer;
import com.acme.blueprint.customers.domain.CustomerProfile;
import com.acme.blueprint.customers.domain.CustomerRepository;
import com.acme.blueprint.customers.domain.DuplicateNationalCodeException;
import com.acme.blueprint.customers.domain.NationalCode;
import com.acme.blueprint.shared.ConstraintViolations;
import com.acme.blueprint.shared.SortMapping;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

/**
 * Adapts Spring Data JPA to the module's ports: the domain's
 * {@link CustomerRepository} and the application's {@link CustomerFinder}.
 * Translating typed ids, value objects and persistence exceptions happens
 * here and only here.
 */
@Repository
class CustomerPersistenceAdapter implements CustomerRepository, CustomerFinder {

    /** Must match the constraint name in V2026_10_17_1000__create_customer.sql. */
    private static final String NATIONAL_CODE_UNIQUE = "customer_national_code_uk";
    private static final char LIKE_ESCAPE = '\\';

    /**
     * The finder's public sort keys → entity paths, next to the query that uses
     * them. Each has an index ending in the id tiebreaker (see the migration).
     */
    private static final SortMapping SORTING = SortMapping.of(Map.of(
            CustomerFinder.SORT_BY_CREATED_AT, "createdAt",
            CustomerFinder.SORT_BY_LAST_NAME, "profile.lastName"), "id");

    private final SpringDataCustomerRepository jpa;

    CustomerPersistenceAdapter(SpringDataCustomerRepository jpa) {
        this.jpa = jpa;
    }

    /** Flushes, so the unique constraint fires now instead of at commit. */
    @Override
    public Customer add(Customer customer) {
        try {
            return jpa.saveAndFlush(customer);
        } catch (DataIntegrityViolationException exception) {
            if (ConstraintViolations.isViolationOf(exception, NATIONAL_CODE_UNIQUE)) {
                throw new DuplicateNationalCodeException(customer.nationalCode());
            }
            throw exception;
        }
    }

    @Override
    public Optional<Customer> findById(CustomerId id) {
        return jpa.findById(id.value());
    }

    @Override
    public boolean existsByNationalCode(NationalCode nationalCode) {
        return jpa.existsByNationalCode(nationalCode.value());
    }

    @Override
    public Page<Customer> search(CustomerSearchCriteria criteria, Pageable pageable) {
        return jpa.findAll(matching(criteria), SORTING.translate(pageable));
    }


    private static Specification<Customer> matching(CustomerSearchCriteria criteria) {
        return (root, query, cb) -> {
            Path<CustomerProfile> profile = root.get("profile");
            List<Predicate> predicates = new ArrayList<>();
            if (criteria.status() != null) {
                predicates.add(cb.equal(root.get("status"), criteria.status()));
            }
            if (criteria.tier() != null) {
                predicates.add(cb.equal(profile.get("tier"), criteria.tier()));
            }
            if (criteria.search() != null && !criteria.search().isBlank()) {
                String pattern = "%" + escapeLike(criteria.search().trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        containsIgnoringCase(cb, profile.get("firstName"), pattern),
                        containsIgnoringCase(cb, profile.get("lastName"), pattern),
                        containsIgnoringCase(cb, profile.get("email"), pattern),
                        cb.like(profile.get("mobile"), pattern, LIKE_ESCAPE),
                        cb.like(root.get("nationalCode"), pattern, LIKE_ESCAPE)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Predicate containsIgnoringCase(CriteriaBuilder cb, Path<String> attribute, String pattern) {
        return cb.like(cb.lower(attribute), pattern, LIKE_ESCAPE);
    }

    /** User input must not be able to inject LIKE wildcards. */
    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
