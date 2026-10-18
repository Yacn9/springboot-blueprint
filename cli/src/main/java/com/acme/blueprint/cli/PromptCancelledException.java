package com.acme.blueprint.cli;

/** The user pressed Ctrl-C at a question: nothing is created. */
final class PromptCancelledException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    PromptCancelledException() {
        super("Cancelled.");
    }
}
