package com.acme.blueprint.customers.application;

import com.acme.blueprint.customers.domain.CustomerStatus;
import com.acme.blueprint.customers.domain.CustomerTier;

/**
 * Filters for the customer list. Every field is optional; {@code null} means
 * "do not filter on this". {@code search} matches name, email, mobile or
 * national code.
 */
public record CustomerSearchCriteria(String search, CustomerStatus status, CustomerTier tier) {
}
