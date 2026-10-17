package com.acme.blueprint.users.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** The profile enforces its own rules, whichever entry point builds it. */
class UserProfileTest {

    @Test
    void acceptsAValidProfile() {
        assertThatCode(() -> profile("Vesen", "09121234567", "vesen@example.com", null)).doesNotThrowAnyException();
    }

    @Test
    void rejectsABlankName() {
        assertRejected(() -> profile(" ", "09121234567", "vesen@example.com", null), "firstName");
    }

    @Test
    void rejectsAMalformedMobile() {
        assertRejected(() -> profile("Vesen", "9121234567", "vesen@example.com", null), "mobile");
    }

    @Test
    void rejectsSomethingThatIsNotAnEmail() {
        assertRejected(() -> profile("Vesen", "09121234567", "ali.example.com", null), "email");
    }

    @Test
    void rejectsNotesLongerThanTheColumn() {
        String tooLong = "x".repeat(UserProfile.NOTES_MAX_LENGTH + 1);
        assertRejected(() -> profile("Vesen", "09121234567", "vesen@example.com", tooLong), "notes");
    }

    private static void assertRejected(Runnable construction, String field) {
        assertThatThrownBy(construction::run)
                .isInstanceOf(InvalidUserProfileException.class)
                .hasMessageContaining("'" + field + "'")
                .extracting("code").isEqualTo("users.invalid-profile");
    }

    private static UserProfile profile(String firstName, String mobile, String email, String notes) {
        return new UserProfile(firstName, "Naqibi", mobile, email, UserTier.GOLD, "Tehran Central", notes);
    }
}
