package com.acme.blueprint.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.util.Optional;

/**
 * Where answers come from: typed lines everywhere, and on an interactive
 * terminal single key presses too — what lets a list be picked from with the
 * arrow keys instead of typed.
 */
interface Keyboard {

    /** Shows {@code prompt} and reads one line; null when the input ends (a closed pipe, Ctrl-D). */
    String readLine(String prompt);

    /** Key presses, the terminal in raw mode until they are closed — or empty where only whole lines can be read. */
    Optional<KeyPresses> keyPresses();

    enum Key { UP, DOWN, SPACE, ENTER, INTERRUPT, END, OTHER }

    /** One key at a time, and the screen to draw on meanwhile. */
    interface KeyPresses extends AutoCloseable {

        Key next();

        PrintWriter screen();

        /** Columns, or 0 when unknown. */
        int width();

        @Override
        void close();
    }

    /** Lines only — pipes, CI, tests: the prompt goes to {@code output}, the answer comes from {@code input}. */
    static Keyboard lines(BufferedReader input, PrintWriter output) {
        return new Keyboard() {
            @Override
            public String readLine(String prompt) {
                output.print(prompt);
                output.flush();
                try {
                    return input.readLine();
                } catch (IOException failure) {
                    throw new UncheckedIOException(failure);
                }
            }

            @Override
            public Optional<KeyPresses> keyPresses() {
                return Optional.empty();
            }
        };
    }
}
