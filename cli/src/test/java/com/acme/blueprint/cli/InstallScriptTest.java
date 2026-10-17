package com.acme.blueprint.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs the real {@code install.sh} with a stand-in {@code jbang} that records its
 * arguments and runs the jar this build produced — so the script's own work
 * (finding JBang, passing arguments, input and exit code through) is exercised
 * without the network. Installing JBang itself is JBang's installer's job.
 */
class InstallScriptTest {

    private static final Path SCRIPT = Path.of(System.getProperty("blueprint.install.script"));
    private static final Path CLI_JAR = Path.of(System.getProperty("blueprint.cli.jar"));

    /** Skips JBang's options, then runs {@code <jar> <args…>} with the test's Java. */
    private static final String FAKE_JBANG = """
            #!/bin/sh
            printf '%s\\n' "$*" > "$JBANG_ARGS"
            while [ "${1#-}" != "$1" ]; do
              case "$1" in --java) shift 2 ;; *) shift ;; esac
            done
            jar="$1"; shift
            exec "$TEST_JAVA" -jar "$jar" "$@"
            """;

    @TempDir
    Path work;

    private Path home;
    private Path cwd;

    private record Run(int exitCode, String output) {
    }

    @BeforeEach
    void installFakeJbang() throws Exception {
        assumeTrue(!System.getProperty("os.name").toLowerCase().contains("win"), "needs a POSIX sh");
        home = Files.createDirectories(work.resolve("home"));
        cwd = Files.createDirectories(work.resolve("cwd"));
        Path jbang = Files.createDirectories(home.resolve(".jbang/bin")).resolve("jbang");
        Files.writeString(jbang, FAKE_JBANG);
        assertThat(jbang.toFile().setExecutable(true)).isTrue();
    }

    @Test
    void runsBlueprintNewThroughJbangWithTheGivenName() throws Exception {
        Run run = install("", "my-shop");

        assertThat(run.exitCode()).as(run.output()).isZero();
        assertThat(run.output()).contains("Created my-shop");
        assertThat(Files.readString(cwd.resolve("my-shop/settings.gradle.kts"))).contains("rootProject.name = \"my-shop\"");
        assertThat(Files.readString(work.resolve("jbang-args")))
                .isEqualTo("--fresh --java 17+ " + CLI_JAR + " new my-shop\n");
    }

    @Test
    void asksForTheNameWhenNoneIsGiven() throws Exception {
        Run run = install("order-hub\n");

        assertThat(run.exitCode()).as(run.output()).isZero();
        assertThat(run.output()).contains("? Project name");
        assertThat(cwd.resolve("order-hub")).isDirectory();
    }

    @Test
    void passesTheCliExitCodeThrough() throws Exception {
        Run run = install("", "Bad!Name");

        assertThat(run.exitCode()).isEqualTo(BlueprintCli.USAGE_ERROR);
        assertThat(run.output()).contains("error: 'Bad!Name' is not a valid project name");
    }

    private Run install(String stdin, String... arguments) throws Exception {
        List<String> command = Stream.concat(Stream.of("sh", SCRIPT.toString()), Stream.of(arguments)).toList();
        ProcessBuilder process = new ProcessBuilder(command).directory(cwd.toFile()).redirectErrorStream(true);
        Map<String, String> environment = process.environment();
        // No jbang on the PATH: the script must find the one in ~/.jbang/bin.
        environment.put("PATH", "/usr/bin:/bin");
        environment.put("HOME", home.toString());
        environment.put("BLUEPRINT_CLI", CLI_JAR.toString());
        environment.put("JBANG_ARGS", work.resolve("jbang-args").toString());
        environment.put("TEST_JAVA", Path.of(System.getProperty("java.home"), "bin", "java").toString());

        Process started = process.start();
        started.getOutputStream().write(stdin.getBytes(UTF_8));
        started.getOutputStream().close();
        String output;
        try (InputStream stream = started.getInputStream()) {
            output = new String(stream.readAllBytes(), UTF_8);
        }
        assertThat(started.waitFor(60, TimeUnit.SECONDS)).as("install.sh finished in time: " + output).isTrue();
        return new Run(started.exitValue(), output);
    }
}
