package com.acme.blueprint.users.domain;

import static com.acme.blueprint.users.domain.UserStatus.ACTIVE;
import static com.acme.blueprint.users.domain.UserStatus.CLOSED;
import static com.acme.blueprint.users.domain.UserStatus.PENDING;
import static com.acme.blueprint.users.domain.UserStatus.SUSPENDED;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.acme.blueprint.users.UserId;

import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Aggregate root of the users module.
 *
 * <p>State changes go through intention-revealing methods that enforce the
 * invariants — there are no setters. The class doubles as the Hibernate
 * entity (a deliberate, documented trade-off: one model instead of a domain
 * model plus a mirrored persistence model), but the domain never imports
 * Spring, so it stays unit-testable without a container.
 */
@Entity
@Table(name = "user_account", schema = "users")
public class User {

    @Id
    private UUID id;

    /** Optimistic locking; {@code null} also tells Spring Data the entity is new. */
    @Version
    private Long version;

    private String nationalCode;

    @Embedded
    private UserProfile profile;

    @Enumerated(EnumType.STRING)
    private UserStatus status;

    private Instant createdAt;

    private Instant updatedAt;

    /** For Hibernate only. */
    protected User() {
    }

    private User(UserId id, NationalCode nationalCode, UserProfile profile, Instant now) {
        this.id = id.value();
        this.nationalCode = nationalCode.value();
        this.profile = Objects.requireNonNull(profile, "profile");
        this.status = PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** A newly registered user starts as {@link UserStatus#PENDING}. */
    public static User register(NationalCode nationalCode, UserProfile profile, Instant now) {
        return new User(UserId.newId(), nationalCode, profile, now);
    }

    public void updateProfile(UserProfile newProfile, Instant now) {
        if (status == CLOSED) {
            throw new UserClosedException(id());
        }
        this.profile = Objects.requireNonNull(newProfile, "newProfile");
        this.updatedAt = now;
    }

    /** Moving to the current status is a no-op, so retried requests are harmless. */
    public void changeStatus(UserStatus target, Instant now) {
        if (target == status) {
            return;
        }
        if (!canTransition(status, target)) {
            throw new UserStatusTransitionException(id(), status, target);
        }
        this.status = target;
        this.updatedAt = now;
    }

    private static boolean canTransition(UserStatus from, UserStatus to) {
        return switch (from) {
            case PENDING -> to == ACTIVE || to == CLOSED;
            case ACTIVE -> to == SUSPENDED || to == CLOSED;
            case SUSPENDED -> to == ACTIVE || to == CLOSED;
            case CLOSED -> false;
        };
    }

    public UserId id() {
        return new UserId(id);
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

    public UserProfile profile() {
        return profile;
    }

    public UserStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
