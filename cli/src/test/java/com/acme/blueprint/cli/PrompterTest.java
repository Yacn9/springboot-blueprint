package com.acme.blueprint.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.acme.blueprint.cli.Keyboard.Key;
import com.acme.blueprint.cli.Keyboard.KeyPresses;

import org.junit.jupiter.api.Test;

class PrompterTest {

    private static final List<Choice> STACKS = List.of(
            Choice.of("none", "None"),
            Choice.of("prometheus", "Prometheus"),
            Choice.soon("elk", "ELK"),
            Choice.of("lgtm", "LGTM"));

    private final StringWriter written = new StringWriter();

    private Prompter prompterReading(String typed) {
        return new Prompter(new BufferedReader(new StringReader(typed)), new PrintWriter(written));
    }

    /** A terminal that presses the given keys, in order, then reports the end of the input. */
    private Prompter prompterPressing(Key... keys) {
        Deque<Key> pending = new ArrayDeque<>(List.of(keys));
        PrintWriter screen = new PrintWriter(written);
        Keyboard keyboard = new Keyboard() {
            @Override
            public String readLine(String prompt) {
                return null;
            }

            @Override
            public Optional<KeyPresses> keyPresses() {
                return Optional.of(new KeyPresses() {
                    @Override
                    public Key next() {
                        return pending.isEmpty() ? Key.END : pending.removeFirst();
                    }

                    @Override
                    public PrintWriter screen() {
                        return screen;
                    }

                    @Override
                    public int width() {
                        return 80;
                    }

                    @Override
                    public void close() {
                    }
                });
            }
        };
        return new Prompter(keyboard, screen, Style.PLAIN);
    }

    private static Integer positiveNumber(String answer) {
        int number = Integer.parseInt(answer.strip());
        if (number <= 0) {
            throw new IllegalArgumentException("Must be positive.");
        }
        return number;
    }

    @Test
    void returnsTheParsedAnswer() {
        Optional<Integer> answer = prompterReading("42\n").ask("Number", PrompterTest::positiveNumber);

        assertThat(answer).contains(42);
        assertThat(written).hasToString("? Number: ");
    }

    @Test
    void printsWhyAnAnswerWasRejectedAndAsksAgain() {
        Optional<Integer> answer = prompterReading("-1\nseven\n7\n").ask("Number", PrompterTest::positiveNumber);

        assertThat(answer).contains(7);
        assertThat(written.toString())
                .containsOnlyOnce("Must be positive.")
                .contains("For input string: \"seven\"");
        assertThat(written.toString().split("\\? Number: ", -1)).hasSize(4);
    }

    @Test
    void returnsEmptyWhenTheInputEnds() {
        Optional<Integer> answer = prompterReading("").ask("Number", PrompterTest::positiveNumber);

        assertThat(answer).isEmpty();
        assertThat(written.toString()).isEqualTo("? Number: " + System.lineSeparator());
    }

    @Test
    void returnsEmptyWhenTheInputEndsAfterARejectedAnswer() {
        Optional<Integer> answer = prompterReading("0\n").ask("Number", PrompterTest::positiveNumber);

        assertThat(answer).isEmpty();
        assertThat(written.toString()).contains("Must be positive.");
    }

    @Test
    void typedPicksListOnlyWhatCanBePickedAndEnterTakesTheDefault() {
        assertThat(prompterReading("\n").select("Observability", STACKS, "prometheus")).isEqualTo("prometheus");
        assertThat(written.toString()).startsWith("? Observability (none / prometheus / lgtm) [prometheus]: ");
    }

    @Test
    void typedPicksRefuseWhatIsComingAndWhatIsUnknown() {
        assertThat(prompterReading("elk\nsplunk\nlgtm\n").select("Observability", STACKS, "none")).isEqualTo("lgtm");
        assertThat(written.toString()).contains("elk is not available yet.", "Choose one of: none, prometheus, lgtm.");
    }

    @Test
    void typedChecklistsTakeIdsAllOrNone() {
        List<Choice> scans = List.of(Choice.of("codeql", "CodeQL"), Choice.of("zap", "ZAP"));

        assertThat(prompterReading("zap, codeql\n").pickAny("Scans", scans, Set.of())).containsExactly("zap", "codeql");
        assertThat(prompterReading("all\n").pickAny("Scans", scans, Set.of())).containsExactly("codeql", "zap");
        assertThat(prompterReading("none\n").pickAny("Scans", scans, Set.of("zap"))).isEmpty();
        assertThat(prompterReading("\n").pickAny("Scans", scans, Set.of("zap"))).containsExactly("zap");
    }

    @Test
    void menusStartAtTheDefaultAndSkipWhatIsComing() {
        // From prometheus, down passes over elk to lgtm; down again wraps to none, up goes back to lgtm.
        String picked = prompterPressing(Key.DOWN, Key.DOWN, Key.UP, Key.ENTER).select("Observability", STACKS, "prometheus");

        assertThat(picked).isEqualTo("lgtm");
        assertThat(written.toString()).contains("❯ Prometheus", "  ELK (soon)", "❯ LGTM")
                .endsWith("? Observability: LGTM" + System.lineSeparator() + "\u001B[?25h");
    }

    @Test
    void menusTakeTheDefaultWhenTheInputEnds() {
        assertThat(prompterPressing().select("Observability", STACKS, "prometheus")).isEqualTo("prometheus");
    }

    @Test
    void checklistsTickWithSpaceAndConfirmWithEnter() {
        List<Choice> scans = List.of(Choice.of("codeql", "CodeQL"), Choice.of("dependency-check", "Dependency-Check"),
                Choice.of("zap", "ZAP"));

        Set<String> ticked = prompterPressing(Key.SPACE, Key.DOWN, Key.DOWN, Key.SPACE, Key.UP, Key.ENTER)
                .pickAny("Scans", scans, Set.of());

        assertThat(ticked).containsExactly("codeql", "zap");
        assertThat(written.toString()).contains("? Scans: CodeQL, ZAP");
    }

    @Test
    void ctrlCInAMenuCancels() {
        assertThatThrownBy(() -> prompterPressing(Key.DOWN, Key.INTERRUPT).select("Observability", STACKS, "none"))
                .isInstanceOf(PromptCancelledException.class).hasMessage("Cancelled.");
    }

    @Test
    void theDefaultSetupAnswersEveryListWithoutAsking() {
        Prompter defaults = prompterReading("").answeringDefaults();

        assertThat(defaults.select("Observability", STACKS, "prometheus")).isEqualTo("prometheus");
        assertThat(defaults.pickAny("Scans", STACKS, Set.of())).isEmpty();
        assertThat(defaults.type("Version", "4.1.1", typed -> typed)).isEqualTo("4.1.1");
        assertThat(written.toString()).isEmpty();
    }
}
