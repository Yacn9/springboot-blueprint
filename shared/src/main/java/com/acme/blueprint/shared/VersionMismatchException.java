package com.acme.blueprint.shared;

/**
 * The client's {@code If-Match} names a version that is no longer current. One
 * code for every resource, and the same code whether the stale write is caught
 * when the aggregate is loaded or by {@code @Version} at commit — a client
 * handles "reload and retry" in exactly one place.
 */
public final class VersionMismatchException extends PreconditionFailedException {

    public VersionMismatchException(String resource, Object id, long expected, long actual) {
        super("request.version-mismatch",
                "%s %s is at version %d, not %d; reload it and retry".formatted(resource, id, actual, expected));
    }
}
