package com.acme.blueprint.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

/** Asks questions on the terminal until the answer is acceptable. */
final class Prompter {

    private final BufferedReader input;
    private final PrintWriter output;
    private final Style style;
    /** Takes every setting's default without asking — and never says yes to an install. */
    private final boolean defaults;

    Prompter(BufferedReader input, PrintWriter output) {
        this(input, output, Style.PLAIN);
    }

    Prompter(BufferedReader input, PrintWriter output, Style style) {
        this(input, output, style, false);
    }

    private Prompter(BufferedReader input, PrintWriter output, Style style, boolean defaults) {
        this.input = input;
        this.output = output;
        this.style = style;
        this.defaults = defaults;
    }

    /** This prompter, answering every choice with its default instead of asking. */
    Prompter answeringDefaults() {
        return new Prompter(input, output, style, true);
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
            output.print(style.prompt(question));
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
                output.println(style.red(rejected.getMessage()));
            }
        }
    }

    /**
     * Asks for one of {@code options}; Enter, or the end of the input, takes the
     * first. A single option is shown, not asked.
     */
    String choose(String question, List<String> options) {
        String first = options.get(0);
        if (defaults) {
            return first;
        }
        if (options.size() == 1) {
            output.println(style.prompt(question) + style.cyan(first));
            output.flush();
            return first;
        }
        return ask(question + " (" + String.join(" / ", options) + ") [" + first + "]", answer -> {
            String picked = answer.isBlank() ? first : answer.strip();
            if (!options.contains(picked)) {
                throw new IllegalArgumentException("Choose one of: " + String.join(", ", options) + ".");
            }
            return picked;
        }).orElse(first);
    }

    /** Asks yes or no; Enter means yes, the end of the input means no — nothing is installed unasked. */
    boolean confirm(String question) {
        return !defaults && confirm(question, false);
    }

    /** Asks yes or no; Enter means yes, the end of the input means {@code whenUnanswered}. */
    boolean confirm(String question, boolean whenUnanswered) {
        return confirm(question, true, whenUnanswered);
    }

    /** Asks yes or no; Enter means {@code byDefault}, the end of the input {@code whenUnanswered}. */
    boolean confirm(String question, boolean byDefault, boolean whenUnanswered) {
        if (defaults) {
            return byDefault;
        }
        return ask(question + (byDefault ? " [Y/n]" : " [y/N]"), answer -> switch (answer.strip().toLowerCase(Locale.ROOT)) {
            case "" -> byDefault;
            case "y", "yes" -> true;
            case "n", "no" -> false;
            default -> throw new IllegalArgumentException("Answer y or n.");
        }).orElse(whenUnanswered);
    }

    private String readLine() {
        try {
            return input.readLine();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
