package com.acme.blueprint.cli;

/** A project name the generator cannot turn into a package, a class prefix and a Gradle project. */
final class InvalidProjectNameException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    InvalidProjectNameException(String message) {
        super(message);
    }
}
