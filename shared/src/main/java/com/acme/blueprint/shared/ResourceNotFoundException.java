package com.acme.blueprint.shared;

/** The resource addressed by the request does not exist. Maps to 404. */
public abstract non-sealed class ResourceNotFoundException extends BusinessException {

    protected ResourceNotFoundException(String code, String message) {
        super(code, message);
    }
}
