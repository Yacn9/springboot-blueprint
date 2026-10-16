package com.acme.blueprint.cli;

import static com.acme.blueprint.cli.TestArchives.text;
import static com.acme.blueprint.cli.TestArchives.zip;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class NewCommandTest {

    private static final byte[] ARCHIVE = zip(Map.of(
            "settings.gradle.kts", text("rootProject.name = \"modular-monolith-blueprint\"\n")));
    private static final ProjectTemplate TEMPLATE =
            new ProjectTemplate(() -> new ByteArrayInputStream(ARCHIVE), "# {{name}}");

    @TempDir
    Path work;

    /** What a run printed and returned. */
    private record Run(int exitCode, String out, String err) {
    }

    private Run run(ProjectTemplate template, String stdin, String... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cli = BlueprintCli.commandLine(template, new BufferedReader(new StringReader(stdin)));
        cli.setColorScheme(CommandLine.Help.defaultColorScheme(CommandLine.Help.Ansi.OFF));
        cli.setOut(new PrintWriter(out, true));
        cli.setErr(new PrintWriter(err, true));
        int exitCode = cli.execute(args);
        return new Run(exitCode, out.toString(), err.toString());
    }

    private Run run(String stdin, String... args) {
        return run(TEMPLATE, stdin, args);
    }

    private Run runNew(String stdin, String... args) {
        String[] full = new String[args.length + 3];
        full[0] = "new";
        System.arraycopy(args, 0, full, 1, args.length);
        full[args.length + 1] = "--parent-dir";
        full[args.length + 2] = work.toString();
        return run(stdin, full);
    }

    @Test
    void createsTheProjectNamedOnTheCommandLine() {
        Run run = runNew("", "my-shop");

        assertThat(run.exitCode()).isZero();
        assertThat(work.resolve("my-shop/settings.gradle.kts")).hasContent("rootProject.name = \"my-shop\"\n");
        assertThat(run.out()).contains("Created my-shop", "com.myshop", "Next steps", "./gradlew build");
        assertThat(run.out()).doesNotContain("? Project name");
        assertThat(run.err()).isEmpty();
    }

    @Test
    void theParentDirectoryHoldsTheProjectDirectoryNotTheProjectItself() {
        runNew("", "my-shop");

        assertThat(work.resolve("my-shop/settings.gradle.kts")).exists();
        assertThat(work.resolve("settings.gradle.kts")).doesNotExist();
    }

    @Test
    void acceptsTheShortOptionForTheParentDirectory() {
        Run run = run("", "new", "my-shop", "-d", work.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(work.resolve("my-shop")).isDirectory();
    }

    @Test
    void asksForTheNameWhenItIsLeftOut() {
        Run run = runNew("my-shop\n");

        assertThat(run.exitCode()).isZero();
        assertThat(run.out()).contains("? Project name");
        assertThat(work.resolve("my-shop")).isDirectory();
    }

    @Test
    void asksAgainUntilTheNameIsValid() {
        Run run = runNew("1bad\nclass\nmy-shop\n");

        assertThat(run.exitCode()).isZero();
        assertThat(run.out()).contains("not a valid project name", "reserved word");
        assertThat(run.out().split("\\? Project name", -1)).hasSize(4);
        assertThat(work.resolve("my-shop")).isDirectory();
    }

    @Test
    void normalisesTheNameItIsGiven() {
        Run run = runNew("", "Order_Hub");

        assertThat(run.exitCode()).isZero();
        assertThat(work.resolve("order-hub")).isDirectory();
    }

    @Test
    void givesUpWithAUsageErrorWhenTheInputEnds() throws IOException {
        Run run = runNew("1bad\n");

        assertThat(run.exitCode()).isEqualTo(BlueprintCli.USAGE_ERROR);
        assertThat(run.err()).contains("error: No project name given", "Try 'blueprint new --help'");
        try (var children = Files.list(work)) {
            assertThat(children).isEmpty();
        }
    }

    @Test
    void reportsAnInvalidNameGivenAsAnArgumentAsAUsageErrorWithoutAsking() {
        Run run = runNew("", "Bad!Name");

        assertThat(run.exitCode()).isEqualTo(BlueprintCli.USAGE_ERROR);
        assertThat(run.err()).contains("error: 'Bad!Name' is not a valid project name", "Try 'blueprint new --help'");
        assertThat(run.out()).doesNotContain("? Project name");
    }

    @Test
    void reportsAnUnknownOptionAsAUsageErrorWithoutAStackTraceOrTheWholeUsage() {
        Run run = runNew("", "my-shop", "--bogus");

        assertThat(run.exitCode()).isEqualTo(BlueprintCli.USAGE_ERROR);
        assertThat(run.err()).contains("error:", "--bogus", "Try 'blueprint new --help'")
                .doesNotContain("Usage:").doesNotContain("Exception");
    }

    @Test
    void reportsASecondWordOfANameLeftUnquotedAsAUsageError() {
        Run run = runNew("", "Order", "Hub");

        assertThat(run.exitCode()).isEqualTo(BlueprintCli.USAGE_ERROR);
        assertThat(run.err()).contains("error:", "Hub");
    }

    @Test
    void refusesToOverwriteAnExistingProjectAndSaysHowToGoOn() throws IOException {
        Files.createDirectories(work.resolve("my-shop"));
        Files.writeString(work.resolve("my-shop/keep.txt"), "mine");

        Run run = runNew("", "my-shop");

        assertThat(run.exitCode()).isEqualTo(BlueprintCli.GENERATION_FAILED);
        assertThat(run.err()).contains("error:", "already exists and is not empty", "another project name or parent directory");
        assertThat(work.resolve("my-shop/keep.txt")).hasContent("mine");
    }

    @Test
    void reportsAMissingTemplateAsAnErrorLine() {
        ProjectTemplate missing = new ProjectTemplate(() -> {
            throw new ProjectGenerationException("This build has no project template.");
        }, "# {{name}}");

        Run run = run(missing, "", "new", "my-shop", "--parent-dir", work.toString());

        assertThat(run.exitCode()).isEqualTo(BlueprintCli.GENERATION_FAILED);
        assertThat(run.err()).isEqualTo("error: This build has no project template." + System.lineSeparator());
    }

    @Test
    void reportsAnUnexpectedIoFailureAsAnErrorLineNotAStackTrace() {
        ProjectTemplate broken = new ProjectTemplate(() -> {
            throw new UncheckedIOException(new IOException("disk on fire"));
        }, "# {{name}}");

        Run run = run(broken, "", "new", "my-shop", "--parent-dir", work.toString());

        assertThat(run.exitCode()).isEqualTo(BlueprintCli.GENERATION_FAILED);
        assertThat(run.err()).startsWith("error: ").contains("disk on fire").doesNotContain("\tat ");
    }

    @Test
    void quotesThePathToEnterWhenItContainsSpaces() {
        Path spaced = work.resolve("with space");

        Run run = run("", "new", "my-shop", "--parent-dir", spaced.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.out()).contains("cd \"" + spaced.resolve("my-shop").toAbsolutePath() + "\"");
    }

    @Test
    void explainsTheCommandWithExamplesRulesAndExitCodes() {
        Run run = run("", "new", "--help");

        assertThat(run.exitCode()).isZero();
        assertThat(run.out()).contains("--parent-dir", "<dir>/<project-name>", "Examples:", "blueprint new my-shop",
                "up to 40", "Exit codes: 0 project created, 1 could not create it, 2 bad command line.");
    }

    @Test
    void printsUsageWhenRunWithoutACommand() {
        Run run = run("");

        assertThat(run.exitCode()).isZero();
        assertThat(run.out()).contains("Usage: blueprint", "new");
    }

    @Test
    void printsTheVersion() {
        Run run = run("", "--version");

        assertThat(run.exitCode()).isZero();
        assertThat(run.out()).startsWith("blueprint ");
    }
}
