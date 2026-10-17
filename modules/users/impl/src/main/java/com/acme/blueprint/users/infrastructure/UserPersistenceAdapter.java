package com.acme.blueprint.users.infrastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.acme.blueprint.users.UserId;
import com.acme.blueprint.users.application.UserFinder;
import com.acme.blueprint.users.application.UserSearchCriteria;
import com.acme.blueprint.users.domain.User;
import com.acme.blueprint.users.domain.UserProfile;
import com.acme.blueprint.users.domain.UserRepository;
import com.acme.blueprint.users.domain.DuplicateNationalCodeException;
import com.acme.blueprint.users.domain.NationalCode;
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
 * {@link UserRepository} and the application's {@link UserFinder}.
 * Translating typed ids, value objects and persistence exceptions happens
 * here and only here.
 */
@Repository
class UserPersistenceAdapter implements UserRepository, UserFinder {

    /** Must match the constraint name in V2026_10_17_1000__create_user.sql. */
    private static final String NATIONAL_CODE_UNIQUE = "user_national_code_uk";
    private static final char LIKE_ESCAPE = '\\';

    /**
     * The finder's public sort keys → entity paths, next to the query that uses
     * them. Each has an index ending in the id tiebreaker (see the migration).
     */
    private static final SortMapping SORTING = SortMapping.of(Map.of(
            UserFinder.SORT_BY_CREATED_AT, "createdAt",
            UserFinder.SORT_BY_LAST_NAME, "profile.lastName"), "id");

    private final SpringDataUserRepository jpa;

    UserPersistenceAdapter(SpringDataUserRepository jpa) {
        this.jpa = jpa;
    }

    /** Flushes, so the unique constraint fires now instead of at commit. */
    @Override
    public User add(User user) {
        try {
            return jpa.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            if (ConstraintViolations.isViolationOf(exception, NATIONAL_CODE_UNIQUE)) {
                throw new DuplicateNationalCodeException(user.nationalCode());
            }
            throw exception;
        }
    }

    @Override
    public Optional<User> findById(UserId id) {
        return jpa.findById(id.value());
    }

    @Override
    public boolean existsByNationalCode(NationalCode nationalCode) {
        return jpa.existsByNationalCode(nationalCode.value());
    }

    @Override
    public Page<User> search(UserSearchCriteria criteria, Pageable pageable) {
        return jpa.findAll(matching(criteria), SORTING.translate(pageable));
    }


    private static Specification<User> matching(UserSearchCriteria criteria) {
        return (root, query, cb) -> {
            Path<UserProfile> profile = root.get("profile");
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
