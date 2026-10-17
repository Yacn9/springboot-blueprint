package com.acme.blueprint.users.web;

import com.acme.blueprint.users.domain.UserProfile;
import com.acme.blueprint.users.domain.UserTier;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * JSON shape of a user profile — the same object in the register body, the
 * profile update body and every response, so a client can GET, edit and PUT
 * it back. Split it once a response-only field appears.
 *
 * <p>The limits come from {@link UserProfile}, the single source of these
 * rules; the annotations only make HTTP clients fail fast with every bad
 * field listed.
 */
record UserProfileDto(
        @NotBlank @Size(max = UserProfile.NAME_MAX_LENGTH) String firstName,
        @NotBlank @Size(max = UserProfile.NAME_MAX_LENGTH) String lastName,
        @NotBlank @Pattern(regexp = UserProfile.MOBILE_PATTERN, message = "must be an Iranian mobile number (09xxxxxxxxx)") String mobile,
        @NotBlank @Email(regexp = UserProfile.EMAIL_PATTERN) @Size(max = UserProfile.EMAIL_MAX_LENGTH) String email,
        @NotNull UserTier tier,
        @NotBlank @Size(max = UserProfile.BRANCH_MAX_LENGTH) String branch,
        @Size(max = UserProfile.NOTES_MAX_LENGTH) String notes) {

    static UserProfileDto from(UserProfile profile) {
        return new UserProfileDto(
                profile.firstName(),
                profile.lastName(),
                profile.mobile(),
                profile.email(),
                profile.tier(),
                profile.branch(),
                profile.notes());
    }

    UserProfile toDomain() {
        return new UserProfile(firstName, lastName, mobile, email, tier, branch, notes);
    }
}
