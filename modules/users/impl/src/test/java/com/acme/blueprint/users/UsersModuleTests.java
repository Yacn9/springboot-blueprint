package com.acme.blueprint.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Comparator;

import com.acme.blueprint.users.application.UserFinder;
import com.acme.blueprint.users.application.UserManagement;
import com.acme.blueprint.users.application.UserSearchCriteria;
import com.acme.blueprint.users.domain.User;
import com.acme.blueprint.users.domain.UserProfile;
import com.acme.blueprint.users.domain.UserRepository;
import com.acme.blueprint.users.domain.UserStatus;
import com.acme.blueprint.users.domain.UserTier;
import com.acme.blueprint.users.domain.DuplicateNationalCodeException;
import com.acme.blueprint.users.domain.NationalCode;
import com.acme.blueprint.shared.InvalidSortException;
import com.acme.blueprint.shared.testing.DatabaseTestcontainers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;

/**
 * Boots the users module alone (plus :shared) against a real database,
 * with the production platform settings — Flyway migrations, Hibernate schema
 * validation and the published event are all exercised for real. No other
 * module is on the classpath.
 */
@ApplicationModuleTest
@Import(DatabaseTestcontainers.class)
class UsersModuleTests {

    @Autowired
    UserManagement users;

    @Autowired
    UserRepository repository;

    @Autowired
    UserDirectory directory;

    @Test
    void publishesUserRegisteredWhenAUserIsRegistered(Scenario scenario) {
        scenario.stimulate(() -> users.register(NationalCode.of("0012345679"), profile("ali@example.com")))
                .andWaitForEventOfType(UserRegistered.class)
                .toArriveAndVerify(event -> {
                    assertThat(event.fullName()).isEqualTo("Ali Rezaei");
                    assertThat(event.email()).isEqualTo("ali@example.com");
                });
    }

    @Test
    void rejectsASecondUserWithTheSameNationalCode() {
        users.register(NationalCode.of("1234567891"), profile("first@example.com"));

        assertThatThrownBy(() -> users.register(NationalCode.of("1234567891"), profile("second@example.com")))
                .isInstanceOf(DuplicateNationalCodeException.class);
    }

    /** The race the up-front check cannot see: the unique constraint is translated, not leaked as a 500. */
    @Test
    void translatesTheUniqueConstraintIntoADuplicateNationalCode() {
        NationalCode code = NationalCode.of("0076229742");
        repository.add(User.register(code, profile("race-1@example.com"), Instant.now()));

        assertThatThrownBy(() -> repository.add(User.register(code, profile("race-2@example.com"), Instant.now())))
                .isInstanceOf(DuplicateNationalCodeException.class);
    }

    @Test
    void incrementsTheVersionOnEveryCommittedChange() {
        User registered = users.register(NationalCode.of("0499370899"), profile("dir@example.com"));

        User activated = users.changeStatus(registered.id(), registered.version(), UserStatus.ACTIVE);

        assertThat(activated.version()).isEqualTo(registered.version() + 1);
        assertThat(directory.findById(registered.id()))
                .hasValueSatisfying(summary -> assertThat(summary.email()).isEqualTo("dir@example.com"));
    }

    @Test
    void searchesAcrossNameEmailAndTier() {
        users.register(NationalCode.of("0067749828"), profile("search-me@example.com"));

        var byEmail = users.search(new UserSearchCriteria("SEARCH-ME", null, null), Pageable.ofSize(10));
        var byOtherTier = users.search(new UserSearchCriteria("search-me", null, UserTier.BASIC), Pageable.ofSize(10));

        assertThat(byEmail.getContent()).extracting(c -> c.profile().email()).containsExactly("search-me@example.com");
        assertThat(byOtherTier.getContent()).isEmpty();
    }

    /** Runs every public sort key and the status filter against the real schema, so a renamed field fails here. */
    @Test
    void sortsByEveryPublicKeyAndFiltersByStatus() {
        users.register(NationalCode.of("2000000002"), profile("Zand", "zand@example.com"));
        users.register(NationalCode.of("3000000003"), profile("Abbasi", "abbasi@example.com"));
        var pending = new UserSearchCriteria("@example.com", UserStatus.PENDING, null);

        var byLastName = users.search(pending, PageRequest.of(0, 100, Sort.by(UserFinder.SORT_BY_LAST_NAME)));
        var newestFirst = users.search(pending, PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, UserFinder.SORT_BY_CREATED_AT)));
        var active = users.search(new UserSearchCriteria("zand@", UserStatus.ACTIVE, null), Pageable.ofSize(10));

        assertThat(byLastName.getContent()).extracting(c -> c.profile().lastName()).isSorted();
        assertThat(newestFirst.getContent()).extracting(User::createdAt).isSortedAccordingTo(java.util.Comparator.reverseOrder());
        assertThat(active.getContent()).isEmpty();
    }

    @Test
    void rejectsASortKeyThatIsNotPublic() {
        assertThatThrownBy(() -> users.search(
                new UserSearchCriteria(null, null, null), PageRequest.of(0, 10, Sort.by("version"))))
                .isInstanceOf(InvalidSortException.class);
    }

    private static UserProfile profile(String email) {
        return profile("Rezaei", email);
    }

    private static UserProfile profile(String lastName, String email) {
        return new UserProfile("Ali", lastName, "09121234567", email, UserTier.GOLD, "Tehran Central", null);
    }
}
