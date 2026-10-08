/**
 * Customers module — public contract.
 *
 * <p>Everything other modules may know about customers lives in this package:
 * {@link com.acme.blueprint.customers.CustomerDirectory} for synchronous
 * lookups and {@link com.acme.blueprint.customers.CustomerRegistered} for
 * reacting to changes. The implementation ({@code customers-impl}) adds
 * internal sub-packages that no other module can compile against.
 */
package com.acme.blueprint.customers;
