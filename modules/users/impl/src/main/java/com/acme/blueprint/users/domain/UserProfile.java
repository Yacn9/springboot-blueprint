package com.acme.blueprint.users.domain;

import java.util.regex.Pattern;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * The editable part of a user, as one immutable value. An update replaces
 * the whole profile, so a half-applied edit cannot happen. Hibernate maps the
 * record as an embeddable: its components become columns of the user
 * table.
 *
 * <p>This record is the single source of the profile's rules. The web DTO
 * references the constants below in its annotations, so a request is
 * rejected early with every bad field listed — but any other entry point
 * (an import job, a listener) is still checked here.
 *
 * <p>Hibernate builds the record through this constructor when it loads a
 * row, so tightening a rule requires migrating existing data first.
 */
@Embeddable
public record UserProfile(
        String firstName,
        String lastName,
        String mobile,
        String email,
        @Enumerated(EnumType.STRING) UserTier tier,
        String branch,
        String notes) {

    public static final int NAME_MAX_LENGTH = 100;
    public static final int EMAIL_MAX_LENGTH = 254;
    public static final int BRANCH_MAX_LENGTH = 100;
    public static final int NOTES_MAX_LENGTH = 500;
    /** Iranian mobile number: {@code 09} followed by nine digits. */
    public static final String MOBILE_PATTERN = "09\\d{9}";
    /** One {@code @}, no whitespace, a dot in the domain. The DTO's {@code @Email} uses the same pattern. */
    public static final String EMAIL_PATTERN = "[^@\\s]+@[^@\\s]+\\.[^@\\s]+";

    private static final Pattern MOBILE = Pattern.compile(MOBILE_PATTERN);
    private static final Pattern EMAIL = Pattern.compile(EMAIL_PATTERN);

    public UserProfile {
        requireText("firstName", firstName, NAME_MAX_LENGTH);
        requireText("lastName", lastName, NAME_MAX_LENGTH);
        if (mobile == null || !MOBILE.matcher(mobile).matches()) {
            throw new InvalidUserProfileException("mobile", "must match " + MOBILE_PATTERN);
        }
        requireText("email", email, EMAIL_MAX_LENGTH);
        if (!EMAIL.matcher(email).matches()) {
            throw new InvalidUserProfileException("email", "must be an email address");
        }
        if (tier == null) {
            throw new InvalidUserProfileException("tier", "is required");
        }
        requireText("branch", branch, BRANCH_MAX_LENGTH);
        if (notes != null && notes.length() > NOTES_MAX_LENGTH) {
            throw new InvalidUserProfileException("notes", "must be at most " + NOTES_MAX_LENGTH + " characters");
        }
    }

    public String fullName() {
        return firstName + " " + lastName;
    }

    private static void requireText(String field, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new InvalidUserProfileException(field, "must not be blank");
        }
        if (value.length() > maxLength) {
            throw new InvalidUserProfileException(field, "must be at most " + maxLength + " characters");
        }
    }
}
