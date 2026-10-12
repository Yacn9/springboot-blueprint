package com.acme.blueprint.customers.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** The profile enforces its own rules, whichever entry point builds it. */
class CustomerProfileTest {

    @Test
    void acceptsAValidProfile() {
        assertThatCode(() -> profile("Ali", "09121234567", "ali@example.com", null)).doesNotThrowAnyException();
    }

    @Test
    void rejectsABlankName() {
        assertRejected(() -> profile(" ", "09121234567", "ali@example.com", null), "firstName");
    }

    @Test
    void rejectsAMalformedMobile() {
        assertRejected(() -> profile("Ali", "9121234567", "ali@example.com", null), "mobile");
    }

    @Test
    void rejectsSomethingThatIsNotAnEmail() {
        assertRejected(() -> profile("Ali", "09121234567", "ali.example.com", null), "email");
    }

    @Test
    void rejectsNotesLongerThanTheColumn() {
        String tooLong = "x".repeat(CustomerProfile.NOTES_MAX_LENGTH + 1);
        assertRejected(() -> profile("Ali", "09121234567", "ali@example.com", tooLong), "notes");
    }

    private static void assertRejected(Runnable construction, String field) {
        assertThatThrownBy(construction::run)
                .isInstanceOf(InvalidCustomerProfileException.class)
                .hasMessageContaining("'" + field + "'")
                .extracting("code").isEqualTo("customers.invalid-profile");
    }

    private static CustomerProfile profile(String firstName, String mobile, String email, String notes) {
        return new CustomerProfile(firstName, "Rezaei", mobile, email, CustomerTier.GOLD, "Tehran Central", notes);
    }
}
