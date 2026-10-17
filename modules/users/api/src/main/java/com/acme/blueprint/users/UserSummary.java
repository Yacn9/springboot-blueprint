package com.acme.blueprint.users;

/**
 * The view of a user other modules get. Deliberately narrower than the
 * aggregate: add a field here only when a consumer needs it, because every
 * field is a promise the users module must keep.
 */
public record UserSummary(UserId id, String fullName, String email, String mobile) {
}
