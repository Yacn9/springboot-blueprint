package com.acme.blueprint.cli;

import java.io.Console;

/**
 * Colours and symbols on an interactive terminal; the very same words, plain,
 * everywhere else — pipes, CI logs, {@code NO_COLOR}, tests.
 */
final class Style {

    static final Style PLAIN = new Style(false);

    private static final String RESET = "\u001B[0m";

    private final boolean ansi;

    private Style(boolean ansi) {
        this.ansi = ansi;
    }

    static Style forTerminal() {
        Console console = System.console();
        boolean colours = console != null && isTerminal(console) && System.getenv("NO_COLOR") == null
                && !"dumb".equals(System.getenv("TERM"));
        return colours ? new Style(true) : PLAIN;
    }

    /** Console.isTerminal() exists from Java 22, where System.console() is no longer null when redirected. */
    private static boolean isTerminal(Console console) {
        try {
            return (Boolean) Console.class.getMethod("isTerminal").invoke(console);
        } catch (NoSuchMethodException olderJava) {
            return true;
        } catch (ReflectiveOperationException unexpected) {
            return false;
        }
    }

    String bold(String text) {
        return paint("1", text);
    }

    String dim(String text) {
        return paint("2", text);
    }

    String cyan(String text) {
        return paint("36", text);
    }

    String green(String text) {
        return paint("32", text);
    }

    String yellow(String text) {
        return paint("33", text);
    }

    String red(String text) {
        return paint("31", text);
    }

    /** {@code ✔ text} in colour; just the text when plain. */
    String ok(String text) {
        return ansi ? green("✔") + " " + text : text;
    }

    /** {@code ▲ text} in colour; just the text when plain. */
    String warn(String text) {
        return ansi ? yellow("▲") + " " + text : text;
    }

    /** The question mark of a prompt, and the question. */
    String prompt(String question) {
        return ansi ? cyan("?") + " " + bold(question) + dim(" › ") : "? " + question + ": ";
    }

    /** A title line, shown only in colour — plain output starts straight with the first question. */
    String banner() {
        return ansi ? "\n " + paint("1;46;30", " blueprint ") + "  " + dim("Spring Boot modular monolith") + "\n" : "";
    }

    private String paint(String code, String text) {
        return ansi ? "\u001B[" + code + "m" + text + RESET : text;
    }
}
