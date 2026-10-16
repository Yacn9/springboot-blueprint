package com.acme.blueprint.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ProjectNameTest {

    @ParameterizedTest
    @CsvSource({"my-shop,my-shop", "My Shop,my-shop", "my_shop,my-shop", "'  Shop  ',shop", "SHOP2,shop2",
            "a-b-c,a-b-c", "shop-2go,shop-2go"})
    void normalisesWhatAPersonTypes(String typed, String expected) {
        assertThat(ProjectName.fromUserInput(typed).kebabCase()).isEqualTo(expected);
    }

    @Test
    void printsAsTheNormalisedName() {
        assertThat(ProjectName.fromUserInput("Order Hub")).hasToString("order-hub");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1shop", "-shop", "shop-", "my--shop", "shop!", "my.shop", "é"})
    void rejectsNamesThatAreNotLettersDigitsAndHyphens(String typed) {
        assertThatThrownBy(() -> ProjectName.fromUserInput(typed))
                .isInstanceOf(InvalidProjectNameException.class)
                .hasMessageContaining("'" + typed + "' is not a valid project name")
                .hasMessageContaining("spaces and underscores become hyphens")
                .hasMessageContaining("my-shop");
    }

    @Test
    void quotesTheInputAsTypedNotAsNormalised() {
        assertThatThrownBy(() -> ProjectName.fromUserInput("Shop!"))
                .hasMessageContaining("'Shop!'")
                .hasMessageNotContaining("'shop!'");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void rejectsAnEmptyName(String typed) {
        assertThatThrownBy(() -> ProjectName.fromUserInput(typed))
                .isInstanceOf(InvalidProjectNameException.class)
                .hasMessageContaining("must not be empty");
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> ProjectName.fromUserInput(null)).isInstanceOf(InvalidProjectNameException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"class", "new", "in", "java", "gradle", "kotlin"})
    void rejectsWordsThatCannotBeAPackageSegment(String typed) {
        assertThatThrownBy(() -> ProjectName.fromUserInput(typed))
                .isInstanceOf(InvalidProjectNameException.class)
                .hasMessageContaining("reserved word");
    }

    @Test
    void explainsWhenOnlyTheNameWithoutItsHyphensIsReserved() {
        assertThatThrownBy(() -> ProjectName.fromUserInput("c-lass"))
                .hasMessageContaining("'c-lass'")
                .hasMessageContaining("without its hyphens it is 'class'");
    }

    @Test
    void aReservedWordIsOnlyReservedAsAWholeSegment() {
        assertThat(ProjectName.fromUserInput("classic").kebabCase()).isEqualTo("classic");
        assertThat(ProjectName.fromUserInput("my-class").kebabCase()).isEqualTo("my-class");
    }

    @Test
    void acceptsTheLongestName() {
        assertThat(ProjectName.fromUserInput("a".repeat(ProjectName.MAX_LENGTH)).kebabCase()).hasSize(ProjectName.MAX_LENGTH);
    }

    @Test
    void rejectsANameOneCharacterTooLong() {
        assertThatThrownBy(() -> ProjectName.fromUserInput("a".repeat(ProjectName.MAX_LENGTH + 1)))
                .isInstanceOf(InvalidProjectNameException.class)
                .hasMessageContaining("too long");
    }
}
