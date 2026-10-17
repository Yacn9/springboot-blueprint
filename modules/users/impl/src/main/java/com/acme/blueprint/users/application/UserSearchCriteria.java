package com.acme.blueprint.users.application;

import com.acme.blueprint.users.domain.UserStatus;
import com.acme.blueprint.users.domain.UserTier;

/**
 * Filters for the user list. Every field is optional; {@code null} means
 * "do not filter on this". {@code search} matches name, email, mobile or
 * national code.
 */
public record UserSearchCriteria(String search, UserStatus status, UserTier tier) {
}
