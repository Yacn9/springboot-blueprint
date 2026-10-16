package com.acme.blueprint.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.function.Function;

/** Asks questions on the terminal until the answer is acceptable. */
final class Prompter {

    private final BufferedReader input;
    private final PrintWriter output;

    Prompter(BufferedReader input, PrintWriter output) {
        this.input = input;
        this.output = output;
    }

    /**
     * Asks {@code question} and hands each answer to {@code parser}; when the
     * parser rejects it with an {@link IllegalArgumentException}, prints its
     * message and asks again.
     *
     * @return the parsed answer, or empty when the input ends first (a closed pipe, Ctrl-D)
     */
    <T> Optional<T> ask(String question, Function<String, T> parser) {
        while (true) {
            output.print("? " + question + ": ");
            output.flush();
            String answer = readLine();
            if (answer == null) {
                output.println();
                output.flush();
                return Optional.empty();
            }
            try {
                return Optional.of(parser.apply(answer));
            } catch (IllegalArgumentException rejected) {
                output.println(rejected.getMessage());
            }
        }
    }

    private String readLine() {
        try {
            return input.readLine();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
