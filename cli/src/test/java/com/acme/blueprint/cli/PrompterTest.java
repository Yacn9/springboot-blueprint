package com.acme.blueprint.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class PrompterTest {

    private final StringWriter written = new StringWriter();

    private Prompter prompterReading(String typed) {
        return new Prompter(new BufferedReader(new StringReader(typed)), new PrintWriter(written));
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
}
