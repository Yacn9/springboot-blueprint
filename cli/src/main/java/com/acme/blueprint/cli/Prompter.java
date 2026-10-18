package com.acme.blueprint.cli;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import com.acme.blueprint.cli.Keyboard.KeyPresses;

/**
 * Asks questions on the terminal until the answer is acceptable. Lists are
 * menus picked from with the arrow keys where the terminal allows it, and
 * typed — an id, Enter for the default — everywhere else.
 */
final class Prompter {

    private final Keyboard keyboard;
    private final PrintWriter output;
    private final Style style;
    /** Takes every setting's default without asking — and never says yes to an install. */
    private final boolean defaults;

    Prompter(BufferedReader input, PrintWriter output) {
        this(input, output, Style.PLAIN);
    }

    Prompter(BufferedReader input, PrintWriter output, Style style) {
        this(Keyboard.lines(input, output), output, style);
    }

    Prompter(Keyboard keyboard, PrintWriter output, Style style) {
        this(keyboard, output, style, false);
    }

    private Prompter(Keyboard keyboard, PrintWriter output, Style style, boolean defaults) {
        this.keyboard = keyboard;
        this.output = output;
        this.style = style;
        this.defaults = defaults;
    }

    /** This prompter, answering every choice with its default instead of asking. */
    Prompter answeringDefaults() {
        return new Prompter(keyboard, output, style, true);
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
            output.flush();
            String answer = keyboard.readLine(style.prompt(question));
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

    /** Asks for a typed value; Enter, or the end of the input, takes {@code byDefault}. {@code check} rejects by throwing. */
    String type(String question, String byDefault, UnaryOperator<String> check) {
        if (defaults) {
            return byDefault;
        }
        return ask(question + " [" + byDefault + "]", answer -> answer.isBlank() ? byDefault : check.apply(answer.strip()))
                .orElse(byDefault);
    }

    /** One of {@code choices}; Enter, or the end of the input, takes {@code defaultId}. */
    String select(String question, List<Choice> choices, String defaultId) {
        return pick(question, choices, defaultId).orElse(defaultId);
    }

    /**
     * One of {@code choices}; Enter takes {@code defaultId}. A single choice is
     * shown, not asked.
     *
     * @return the id, or empty when the input ends first
     */
    Optional<String> pick(String question, List<Choice> choices, String defaultId) {
        if (defaults) {
            return Optional.of(defaultId);
        }
        if (choices.size() == 1) {
            output.println(style.prompt(question) + style.cyan(choices.get(0).label()));
            output.flush();
            return Optional.of(choices.get(0).id());
        }
        output.flush();
        Optional<KeyPresses> keys = keyboard.keyPresses();
        if (keys.isPresent()) {
            try (KeyPresses presses = keys.get()) {
                return new Menu(presses, style).pickOne(question, choices, defaultId);
            }
        }
        List<String> ids = enabledIds(choices);
        return ask(question + " (" + String.join(" / ", ids) + ") [" + defaultId + "]", answer -> {
            String picked = answer.isBlank() ? defaultId : answer.strip();
            reject(choices, ids, picked);
            return picked;
        });
    }

    /**
     * Any of {@code choices} — a checklist, or ids typed with commas, {@code all}
     * or {@code none}. Enter, or the end of the input, keeps {@code ticked}.
     */
    Set<String> pickAny(String question, List<Choice> choices, Set<String> ticked) {
        if (defaults) {
            return ticked;
        }
        output.flush();
        Optional<KeyPresses> keys = keyboard.keyPresses();
        if (keys.isPresent()) {
            try (KeyPresses presses = keys.get()) {
                return new Menu(presses, style).pickAny(question, choices, ticked).orElse(ticked);
            }
        }
        List<String> ids = enabledIds(choices);
        String shown = ticked.isEmpty() ? "none" : String.join(",", ticked);
        Function<String, Set<String>> parser = answer -> {
            String typed = answer.strip().toLowerCase(Locale.ROOT);
            if (typed.isEmpty()) {
                return ticked;
            }
            if (typed.equals("none")) {
                return Set.of();
            }
            if (typed.equals("all")) {
                return new LinkedHashSet<>(ids);
            }
            Set<String> picked = Arrays.stream(typed.split("[,\\s]+")).collect(Collectors.toCollection(LinkedHashSet::new));
            picked.forEach(id -> reject(choices, ids, id));
            return picked;
        };
        return ask(question + " (" + String.join(", ", ids) + ", all or none) [" + shown + "]", parser).orElse(ticked);
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

    private static List<String> enabledIds(List<Choice> choices) {
        return choices.stream().filter(Choice::enabled).map(Choice::id).toList();
    }

    private static void reject(List<Choice> choices, List<String> ids, String typed) {
        if (ids.contains(typed)) {
            return;
        }
        boolean coming = choices.stream().anyMatch(choice -> choice.id().equals(typed));
        throw new IllegalArgumentException(coming ? typed + " is not available yet."
                : "Choose one of: " + String.join(", ", ids) + ".");
    }
}
