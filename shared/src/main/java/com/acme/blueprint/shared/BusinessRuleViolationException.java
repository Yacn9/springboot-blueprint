package com.acme.blueprint.shared;

/**
 * The request is well-formed but a domain invariant rejects it — an invalid
 * checksum, a forbidden state transition. Maps to 422.
 */
public abstract non-sealed class BusinessRuleViolationException extends BusinessException {

    protected BusinessRuleViolationException(String code, String message) {
        super(code, message);
    }
}
