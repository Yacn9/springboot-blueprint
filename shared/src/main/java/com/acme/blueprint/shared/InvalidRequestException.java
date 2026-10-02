package com.acme.blueprint.shared;

/**
 * The request itself is malformed in a way bean validation cannot express — an
 * unknown sort key, an unparsable {@code If-Match}. Maps to 400.
 */
public abstract non-sealed class InvalidRequestException extends BusinessException {

    protected InvalidRequestException(String code, String message) {
        super(code, message);
    }
}
