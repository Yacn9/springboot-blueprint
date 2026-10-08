package com.acme.blueprint.customers.domain;

import static com.acme.blueprint.customers.domain.CustomerStatus.ACTIVE;
import static com.acme.blueprint.customers.domain.CustomerStatus.CLOSED;
import static com.acme.blueprint.customers.domain.CustomerStatus.PENDING;
import static com.acme.blueprint.customers.domain.CustomerStatus.SUSPENDED;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.acme.blueprint.customers.CustomerId;

import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Aggregate root of the customers module.
 *
 * <p>State changes go through intention-revealing methods that enforce the
 * invariants — there are no setters. The class doubles as the Hibernate
 * entity (a deliberate, documented trade-off: one model instead of a domain
 * model plus a mirrored persistence model), but the domain never imports
 * Spring, so it stays unit-testable without a container.
 */
@Entity
@Table(name = "customer", schema = "customers")
public class Customer {

    @Id
    private UUID id;

    /** Optimistic locking; {@code null} also tells Spring Data the entity is new. */
    @Version
    private Long version;

    private String nationalCode;

    @Embedded
    private CustomerProfile profile;

    @Enumerated(EnumType.STRING)
    private CustomerStatus status;

    private Instant createdAt;

    private Instant updatedAt;

    /** For Hibernate only. */
    protected Customer() {
    }

    private Customer(CustomerId id, NationalCode nationalCode, CustomerProfile profile, Instant now) {
        this.id = id.value();
        this.nationalCode = nationalCode.value();
        this.profile = Objects.requireNonNull(profile, "profile");
        this.status = PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** A newly registered customer starts as {@link CustomerStatus#PENDING}. */
    public static Customer register(NationalCode nationalCode, CustomerProfile profile, Instant now) {
        return new Customer(CustomerId.newId(), nationalCode, profile, now);
    }

    public void updateProfile(CustomerProfile newProfile, Instant now) {
        if (status == CLOSED) {
            throw new CustomerClosedException(id());
        }
        this.profile = Objects.requireNonNull(newProfile, "newProfile");
        this.updatedAt = now;
    }

    /** Moving to the current status is a no-op, so retried requests are harmless. */
    public void changeStatus(CustomerStatus target, Instant now) {
        if (target == status) {
            return;
        }
        if (!canTransition(status, target)) {
            throw new CustomerStatusTransitionException(id(), status, target);
        }
        this.status = target;
        this.updatedAt = now;
    }

    private static boolean canTransition(CustomerStatus from, CustomerStatus to) {
        return switch (from) {
            case PENDING -> to == ACTIVE || to == CLOSED;
            case ACTIVE -> to == SUSPENDED || to == CLOSED;
            case SUSPENDED -> to == ACTIVE || to == CLOSED;
            case CLOSED -> false;
        };
    }

    public CustomerId id() {
        return new CustomerId(id);
    }

    /**
     * Revision for optimistic concurrency, exposed to clients as the ETag.
     * Hibernate starts at 0 and increments on every committed change.
     */
    public long version() {
        return version == null ? 0 : version;
    }

    public NationalCode nationalCode() {
        return new NationalCode(nationalCode);
    }

    public CustomerProfile profile() {
        return profile;
    }

    public CustomerStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
