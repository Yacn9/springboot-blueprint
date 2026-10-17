package com.acme.blueprint.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs the real {@code install.sh} against a local stand-in for the GitHub
 * release — an HTTP server holding the jar this build produced and its
 * checksum — so the whole path is exercised without publishing anything.
 */
class InstallScriptTest {

    private static final Path SCRIPT = Path.of(System.getProperty("blueprint.install.script"));
    private static final Path CLI_JAR = Path.of(System.getProperty("blueprint.cli.jar"));

    @TempDir
    Path work;

    private Path release;
    private Path cache;
    private Path cwd;
    private HttpServer server;
    private final Map<String, AtomicInteger> requests = new ConcurrentHashMap<>();

    /** What a run printed and returned. */
    private record Run(int exitCode, String output) {
    }

    @BeforeEach
    void startFakeRelease() throws Exception {
        assumeTrue(bash() != null, "bash is not available");
        release = Files.createDirectories(work.resolve("release"));
        cache = work.resolve("cache");
        cwd = Files.createDirectories(work.resolve("cwd"));
        Files.copy(CLI_JAR, release.resolve("blueprint-cli.jar"));
        publishChecksum(sha256(release.resolve("blueprint-cli.jar")));

        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            String name = exchange.getRequestURI().getPath().substring(1);
            requests.computeIfAbsent(name, key -> new AtomicInteger()).incrementAndGet();
            Path file = release.resolve(name);
            if (!Files.isRegularFile(file)) {
                exchange.sendResponseHeaders(404, -1);
            } else {
                byte[] body = Files.readAllBytes(file);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopFakeRelease() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void downloadsVerifiesAndRunsTheCliWithTheGivenName() throws Exception {
        Run run = install("", "my-shop");

        assertThat(run.exitCode()).as(run.output()).isZero();
        assertThat(run.output()).contains("Downloading the Blueprint CLI", "Created my-shop");
        assertThat(Files.readString(cwd.resolve("my-shop/settings.gradle.kts"))).contains("rootProject.name = \"my-shop\"");
        assertThat(cachedJars()).hasSize(1);
    }

    @Test
    void keepsStandardInputForThePromptSoTheNameCanBeTyped() throws Exception {
        Run run = install("order-hub\n");

        assertThat(run.exitCode()).as(run.output()).isZero();
        assertThat(run.output()).contains("? Project name");
        assertThat(cwd.resolve("order-hub")).isDirectory();
    }

    @Test
    void reusesTheCachedJarWhileTheChecksumIsUnchanged() throws Exception {
        install("", "first");
        Run second = install("", "second");

        assertThat(second.exitCode()).as(second.output()).isZero();
        assertThat(second.output()).contains("Using the cached Blueprint CLI").doesNotContain("Downloading");
        assertThat(requests.get("blueprint-cli.jar").get()).isEqualTo(1);
        assertThat(requests.get("blueprint-cli.jar.sha256").get()).isEqualTo(2);
        assertThat(cwd.resolve("second")).isDirectory();
    }

    @Test
    void downloadsAgainWhenANewReleaseChangesTheChecksum() throws Exception {
        install("", "first");
        publishAnotherBuild();

        Run second = install("", "second");

        assertThat(second.exitCode()).as(second.output()).isZero();
        assertThat(second.output()).contains("Downloading the Blueprint CLI");
        assertThat(cachedJars()).hasSize(2);
    }

    @Test
    void refusesAJarThatDoesNotMatchItsPublishedChecksum() throws Exception {
        publishChecksum("0".repeat(64));

        Run run = install("", "my-shop");

        assertThat(run.exitCode()).isNotZero();
        assertThat(run.output()).contains("Checksum mismatch", "Nothing was run");
        assertThat(cachedJars()).isEmpty();
        assertThat(cwd.resolve("my-shop")).doesNotExist();
    }

    @Test
    void refusesAChecksumFileThatIsNotASha256() throws Exception {
        Files.writeString(release.resolve("blueprint-cli.jar.sha256"), "not a checksum\n");

        Run run = install("", "my-shop");

        assertThat(run.exitCode()).isNotZero();
        assertThat(run.output()).contains("is not a SHA-256");
        assertThat(cwd.resolve("my-shop")).doesNotExist();
    }

    @Test
    void fallsBackToTheCachedJarWhenTheReleaseCannotBeReached() throws Exception {
        install("", "first");
        server.stop(0);

        Run second = install("", "second");

        assertThat(second.exitCode()).as(second.output()).isZero();
        assertThat(second.output()).contains("using the cached Blueprint CLI");
        assertThat(cwd.resolve("second")).isDirectory();
    }

    @Test
    void failsWithAHintWhenTheReleaseCannotBeReachedAndNothingIsCached() throws Exception {
        server.stop(0);

        Run run = install("", "my-shop");

        assertThat(run.exitCode()).isNotZero();
        assertThat(run.output()).contains("Could not reach", "BLUEPRINT_RELEASE_URL", "--from-source");
    }

    @Test
    void explainsHowToGetJavaWhenThereIsNone() throws Exception {
        Run run = installWith(Map.of("PATH", "/nonexistent"), "", "my-shop");

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.output()).contains("Java 17 or newer is required", "adoptium");
        assertThat(cwd.resolve("my-shop")).doesNotExist();
    }

    @Test
    void passesTheCliExitCodeThrough() throws Exception {
        Run run = install("", "Bad!Name");

        assertThat(run.exitCode()).isEqualTo(BlueprintCli.USAGE_ERROR);
        assertThat(run.output()).contains("error: 'Bad!Name' is not a valid project name");
    }

    // --- helpers ------------------------------------------------------------

    private Run install(String stdin, String... arguments) throws Exception {
        return installWith(Map.of(), stdin, arguments);
    }

    private Run installWith(Map<String, String> extraEnvironment, String stdin, String... arguments) throws Exception {
        List<String> command = Stream.concat(Stream.of(bash(), slashes(SCRIPT)), Stream.of(arguments)).toList();
        ProcessBuilder process = new ProcessBuilder(command).directory(cwd.toFile()).redirectErrorStream(true);
        Map<String, String> environment = process.environment();
        environment.remove("BLUEPRINT_VERSION");
        environment.put("JAVA_HOME", slashes(Path.of(System.getProperty("java.home"))));
        environment.put("BLUEPRINT_HOME", slashes(cache));
        environment.put("BLUEPRINT_RELEASE_URL", "http://127.0.0.1:" + server.getAddress().getPort());
        environment.putAll(extraEnvironment);
        if (extraEnvironment.containsKey("PATH")) {
            environment.remove("JAVA_HOME");
        }

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

    private List<Path> cachedJars() throws IOException {
        if (!Files.isDirectory(cache)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(cache)) {
            return files.filter(file -> file.getFileName().toString().endsWith(".jar")).toList();
        }
    }

    /** A valid jar with a different checksum: the CLI plus one extra entry, as a newer build would be. */
    private void publishAnotherBuild() throws Exception {
        Path jar = release.resolve("blueprint-cli.jar");
        try (ZipInputStream original = new ZipInputStream(Files.newInputStream(CLI_JAR));
                ZipOutputStream another = new ZipOutputStream(Files.newOutputStream(jar))) {
            for (ZipEntry entry = original.getNextEntry(); entry != null; entry = original.getNextEntry()) {
                another.putNextEntry(new ZipEntry(entry.getName()));
                original.transferTo(another);
                another.closeEntry();
            }
            another.putNextEntry(new ZipEntry("META-INF/another-build.txt"));
            another.write("another build".getBytes(UTF_8));
            another.closeEntry();
        }
        publishChecksum(sha256(jar));
    }

    private void publishChecksum(String sha256) throws IOException {
        Files.writeString(release.resolve("blueprint-cli.jar.sha256"), sha256 + "  blueprint-cli.jar\n");
    }

    private static String sha256(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }

    /** Forward slashes read the same in bash on every platform. */
    private static String slashes(Path path) {
        return path.toString().replace('\\', '/');
    }

    /** Git's bash on Windows (the one on the PATH may be WSL's launcher); plain {@code bash} elsewhere. */
    private static String bash() {
        Path gitBash = Path.of("C:/Program Files/Git/bin/bash.exe");
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            return Files.isExecutable(gitBash) ? gitBash.toString() : null;
        }
        return "bash";
    }
}
