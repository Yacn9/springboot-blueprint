package com.acme.blueprint.customers.web;

import com.acme.blueprint.customers.domain.CustomerProfile;
import com.acme.blueprint.customers.domain.CustomerTier;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * JSON shape of a customer profile — the same object in the register body, the
 * profile update body and every response, so a client can GET, edit and PUT
 * it back. Split it once a response-only field appears.
 *
 * <p>The limits come from {@link CustomerProfile}, the single source of these
 * rules; the annotations only make HTTP clients fail fast with every bad
 * field listed.
 */
record CustomerProfileDto(
        @NotBlank @Size(max = CustomerProfile.NAME_MAX_LENGTH) String firstName,
        @NotBlank @Size(max = CustomerProfile.NAME_MAX_LENGTH) String lastName,
        @NotBlank @Pattern(regexp = CustomerProfile.MOBILE_PATTERN, message = "must be an Iranian mobile number (09xxxxxxxxx)") String mobile,
        @NotBlank @Email(regexp = CustomerProfile.EMAIL_PATTERN) @Size(max = CustomerProfile.EMAIL_MAX_LENGTH) String email,
        @NotNull CustomerTier tier,
        @NotBlank @Size(max = CustomerProfile.BRANCH_MAX_LENGTH) String branch,
        @Size(max = CustomerProfile.NOTES_MAX_LENGTH) String notes) {

    static CustomerProfileDto from(CustomerProfile profile) {
        return new CustomerProfileDto(
                profile.firstName(),
                profile.lastName(),
                profile.mobile(),
                profile.email(),
                profile.tier(),
                profile.branch(),
                profile.notes());
    }

    CustomerProfile toDomain() {
        return new CustomerProfile(firstName, lastName, mobile, email, tier, branch, notes);
    }
}
