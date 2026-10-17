/**
 * Users module — public contract.
 *
 * <p>Everything other modules may know about users lives in this package:
 * {@link com.acme.blueprint.users.UserDirectory} for synchronous
 * lookups and {@link com.acme.blueprint.users.UserRegistered} for
 * reacting to changes. The implementation ({@code users-impl}) adds
 * internal sub-packages that no other module can compile against.
 */
package com.acme.blueprint.users;
