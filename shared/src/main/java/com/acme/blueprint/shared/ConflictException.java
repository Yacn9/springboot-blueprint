package com.acme.blueprint.shared;

/** The request collides with existing state, such as a duplicate key. Maps to 409. */
public abstract non-sealed class ConflictException extends BusinessException {

    protected ConflictException(String code, String message) {
        super(code, message);
    }
}
