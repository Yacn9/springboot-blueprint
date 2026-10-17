package com.acme.blueprint.shared;

import java.util.regex.Pattern;

/**
 * Root of every error a module raises on purpose. The hierarchy is sealed into
 * one category per HTTP status, so the web layer maps them with an exhaustive
 * switch; modules extend a category, never this class.
 *
 * <p>{@link #code()} is a stable, machine-readable identifier such as
 * {@code users.not-found}. Clients key translations off the code — the
 * message is for logs and developers and may change at any time.
 */
public abstract sealed class BusinessException extends RuntimeException
        permits InvalidRequestException, ResourceNotFoundException, ConflictException,
                PreconditionFailedException, BusinessRuleViolationException {

    /** {@code <area>.<kebab-case-name>}, e.g. {@code users.not-found}. */
    private static final Pattern CODE_FORMAT = Pattern.compile("[a-z]+(\\.[a-z0-9-]+)+");

    private final String code;

    protected BusinessException(String code, String message) {
        super(message);
        if (code == null || !CODE_FORMAT.matcher(code).matches()) {
            // Catches swapped (code, message) arguments at the throw site, not in a client.
            throw new IllegalArgumentException("Error code must look like 'area.some-name' but was: " + code);
        }
        this.code = code;
    }

    public String code() {
        return code;
    }
}
