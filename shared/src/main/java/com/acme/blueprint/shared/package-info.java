/**
 * Shared kernel: generic types every module may use — the REST envelope, paging
 * and the business-error hierarchy. Types in this package are the module's API;
 * sub-packages ({@code web}, {@code time}) are internal and only contribute beans.
 *
 * <p>Nothing here may know about a business domain. If a type mentions a
 * customer, an order or a notification, it belongs in that module instead.
 */
package com.acme.blueprint.shared;
