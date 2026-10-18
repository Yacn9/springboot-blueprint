package com.acme.blueprint.cli;

import java.io.PrintWriter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.acme.blueprint.cli.Keyboard.KeyPresses;

/**
 * A list picked from with the arrow keys, redrawn in place: ↑/↓ (or k/j) move,
 * Enter picks — or, in a checklist, Space ticks and Enter confirms. Once
 * answered, the list folds into one line: the question and the answer.
 */
final class Menu {

    private static final String CLEAR_DOWN = "\u001B[J";
    private static final String HIDE_CURSOR = "\u001B[?25l";
    private static final String SHOW_CURSOR = "\u001B[?25h";
    private static final String ANSI = "\u001B\\[[0-9;?]*[A-Za-z]";

    private final KeyPresses keys;
    private final PrintWriter screen;
    private final Style style;

    Menu(KeyPresses keys, Style style) {
        this.keys = keys;
        this.screen = keys.screen();
        this.style = style;
    }

    /** The picked choice's id; empty when the input ends. */
    Optional<String> pickOne(String question, List<Choice> choices, String defaultId) {
        int start = Math.max(0, indexOf(choices, defaultId));
        return run(question, choices, start, Set.of(), false, choices.get(start).label())
                .map(picked -> picked.iterator().next());
    }

    /** The ticked choices' ids, in the list's order; empty when the input ends. */
    Optional<Set<String>> pickAny(String question, List<Choice> choices, Set<String> ticked) {
        int start = Math.max(0, indexOf(choices, choices.stream().filter(Choice::enabled).findFirst().orElseThrow().id()));
        return run(question, choices, start, ticked, true, summary(choices, ticked));
    }

    private Optional<Set<String>> run(String question, List<Choice> choices, int start, Set<String> ticked, boolean checklist,
            String unanswered) {
        String hint = checklist ? "↑↓ move · space tick · enter confirm" : "↑↓ move · enter pick";
        String header = style.prompt(question) + style.dim(hint);
        Set<String> marked = new LinkedHashSet<>(ticked);
        int current = start;
        screen.print(HIDE_CURSOR);
        screen.println(header);
        int headerRows = rows(header);
        try {
            int drawn = draw(choices, current, marked, checklist);
            while (true) {
                switch (keys.next()) {
                    case UP -> current = step(choices, current, -1);
                    case DOWN -> current = step(choices, current, 1);
                    case SPACE -> {
                        String id = choices.get(current).id();
                        if (checklist && !marked.remove(id)) {
                            marked.add(id);
                        }
                    }
                    case ENTER -> {
                        Set<String> picked = checklist ? inOrder(choices, marked) : Set.of(choices.get(current).id());
                        fold(question, headerRows + drawn, checklist ? summary(choices, picked) : choices.get(current).label());
                        return Optional.of(picked);
                    }
                    case END -> {
                        fold(question, headerRows + drawn, unanswered);
                        return Optional.empty();
                    }
                    case INTERRUPT -> {
                        fold(question, headerRows + drawn, style.dim("cancelled"));
                        throw new PromptCancelledException();
                    }
                    default -> {
                        continue;
                    }
                }
                screen.print(up(drawn) + CLEAR_DOWN);
                drawn = draw(choices, current, marked, checklist);
            }
        } finally {
            screen.print(SHOW_CURSOR);
            screen.flush();
        }
    }

    /** Draws the list below the header and returns how many rows it took. */
    private int draw(List<Choice> choices, int current, Set<String> marked, boolean checklist) {
        int rows = 0;
        for (int index = 0; index < choices.size(); index++) {
            Choice choice = choices.get(index);
            boolean here = index == current;
            String pointer = here ? style.cyan("❯") : " ";
            String box = checklist ? (marked.contains(choice.id()) ? style.green("◉") : "◯") + " " : "";
            String label = !choice.enabled() ? style.dim(choice.label()) : here ? style.cyan(choice.label()) : choice.label();
            String note = choice.note() == null ? "" : " " + style.dim("(" + choice.note() + ")");
            String row = pointer + " " + box + label + note;
            screen.println(row);
            rows += rows(row);
        }
        screen.flush();
        return rows;
    }

    /** Replaces the header and the list with one line: the question and its answer. */
    private void fold(String question, int rows, String answer) {
        screen.print(up(rows) + CLEAR_DOWN);
        screen.println(style.prompt(question) + style.cyan(answer));
    }

    /** The next enabled choice in that direction, wrapping around. */
    private static int step(List<Choice> choices, int current, int direction) {
        int next = current;
        do {
            next = Math.floorMod(next + direction, choices.size());
        } while (!choices.get(next).enabled() && next != current);
        return next;
    }

    private int rows(String line) {
        int width = keys.width();
        int length = line.replaceAll(ANSI, "").length();
        return width <= 0 || length == 0 ? 1 : (length + width - 1) / width;
    }

    private static String up(int rows) {
        return "\r\u001B[" + rows + "A";
    }

    private static int indexOf(List<Choice> choices, String id) {
        for (int index = 0; index < choices.size(); index++) {
            if (choices.get(index).id().equals(id)) {
                return index;
            }
        }
        return -1;
    }

    private static Set<String> inOrder(List<Choice> choices, Set<String> ids) {
        return choices.stream().map(Choice::id).filter(ids::contains).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String summary(List<Choice> choices, Set<String> ids) {
        String labels = choices.stream().filter(choice -> ids.contains(choice.id())).map(Choice::label)
                .collect(Collectors.joining(", "));
        return labels.isEmpty() ? "none" : labels;
    }
}
