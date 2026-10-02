package com.acme.blueprint.shared;

/**
 * The client's {@code If-Match} names a version that is no longer current —
 * someone else changed the resource since the client read it. Maps to 412.
 */
public abstract non-sealed class PreconditionFailedException extends BusinessException {

    protected PreconditionFailedException(String code, String message) {
        super(code, message);
    }
}
