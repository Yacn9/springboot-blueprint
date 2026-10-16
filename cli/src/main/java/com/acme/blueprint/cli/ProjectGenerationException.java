package com.acme.blueprint.cli;

/** The project could not be created; the message says why in terms a user can act on. */
final class ProjectGenerationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    ProjectGenerationException(String message) {
        super(message);
    }

    ProjectGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
