package com.acme.blueprint.cli;

import static com.acme.blueprint.cli.TestArchives.text;
import static com.acme.blueprint.cli.TestArchives.zip;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/** Choosing Java, Spring Boot and Gradle versions, and checking and installing the machine's tools. */
class VersionsAndToolsTest {

    private static final String SHA_981 = "a".repeat(64);
    private static final String SHA_990 = "b".repeat(64);
    private static final byte[] ARCHIVE = zip(Map.of(
            "settings.gradle.kts", text("rootProject.name = \"modular-monolith-blueprint\"\n"),
            ProjectVersions.CATALOG, text("[versions]\njava = \"25\"\nspring-boot = \"4.1.1\"\nspring-modulith = \"2.1.1\"\n"),
            ProjectVersions.WRAPPER, text("distributionSha256Sum=" + SHA_981
                    + "\ndistributionUrl=https\\://services.gradle.org/distributions/gradle-9.8.1-bin.zip\n")));
    private static final ProjectTemplate TEMPLATE = new ProjectTemplate(() -> new ByteArrayInputStream(ARCHIVE), "# {{name}}");

    /** What the internet would answer: Spring Boot 4.2.0 and 5.0.0 are out, so is Gradle 9.9. */
    private static final Releases RELEASES = new Releases(url -> Optional.ofNullable(Map.of(
            "https://repo1.maven.org/maven2/org/springframework/boot/spring-boot/maven-metadata.xml",
            "<version>3.5.0</version><version>4.0.8</version><version>4.1.1</version><version>4.2.0-RC1</version>"
                    + "<version>4.2.0</version><version>5.0.0</version>",
            "https://services.gradle.org/versions/current", "{ \"version\" : \"9.9\", \"current\" : true }",
            "https://services.gradle.org/distributions/gradle-9.9-bin.zip.sha256", SHA_990 + "\n").get(url)));

    @TempDir
    Path work;

    private final List<List<String>> installed = new ArrayList<>();

    @Test
    void offersNewerReleasesOfTheSameMajorLineNewestFirst() {
        assertThat(RELEASES.springBoot("4.1.1")).containsExactly("4.2.0", "4.1.1");
        assertThat(RELEASES.gradle("9.8.1")).containsExactly("9.9", "9.8.1");
        assertThat(new Releases(url -> Optional.empty()).springBoot("4.1.1")).containsExactly("4.1.1");
        assertThat(Releases.compare("9.10", "9.9.1")).isPositive();
    }

    @Test
    void pinsTheChosenVersionsInTheGeneratedProject() throws Exception {
        String out = run(tools(Optional.of("21.0.2"), Optional.of("2.43.0"), true), "my-shop\nn\n21\n\n\n\n\n");

        assertThat(out).contains("? Java version (LTS) (25 / 21) [25]", "Spring Boot version (4.2.0 / 4.1.1)", "java    21.0.2");
        assertThat(Files.readString(work.resolve("my-shop/" + ProjectVersions.CATALOG)))
                .contains("java = \"21\"", "spring-boot = \"4.2.0\"", "spring-modulith = \"2.1.1\"");
        assertThat(Files.readString(work.resolve("my-shop/" + ProjectVersions.WRAPPER)))
                .contains("gradle-9.9-bin.zip", "distributionSha256Sum=" + SHA_990);
        assertThat(installed).isEmpty();
    }

    @Test
    void installsMissingToolsOnlyWhenTheUserAgrees() throws Exception {
        // Custom; Enter ×10: newest versions, PostgreSQL, no cache, no broker, samples and tests, compose, no deploy; no to the extras and git; then yes to Java, and yes to Gradle (git has no known installer here).
        String out = run(tools(Optional.empty(), Optional.of("1.9.5"), false), "my-shop\nn\n" + "\n".repeat(10) + "n\n\nn\nn\n\nn\ny\ny\n");

        assertThat(out).contains("java    not found", "git     1.9.5 is too old", "Install git yourself");
        assertThat(installed).containsExactly(
                List.of("/jbang", "jdk", "install", "25"),
                List.of("/jbang", "jdk", "default", "25"),
                List.of("/jbang", "app", "setup", "--java"),
                List.of("bash", "-c", "sdk install gradle 9.9"));
    }

    @Test
    void takesEveryDefaultWithOneAnswerAndInstallsNothing() throws Exception {
        String out = run(tools(Optional.empty(), Optional.of("2.43.0"), false), "my-shop\n\n");

        assertThat(out).contains("? Use the default setup?", "Default setup: Java 25 · Spring Boot 4.2.0 · Gradle 9.9 · PostgreSQL",
                "ci", "dependabot", "observability", "git", "java    not found").doesNotContain("? Database", "? Install");
        assertThat(Files.readString(work.resolve("my-shop/" + ProjectVersions.CATALOG))).contains("spring-boot = \"4.2.0\"");
        assertThat(installed).isEmpty();
    }

    @Test
    void neverInstallsWithoutAnAnswer() throws Exception {
        String out = run(tools(Optional.empty(), Optional.empty(), false), "my-shop\n");

        assertThat(out).contains("Skipped. Install it later");
        assertThat(installed).isEmpty();
    }

    @Test
    void initialisesGitOnMainWithOneCommit() throws Exception {
        List<List<String>> git = new ArrayList<>();
        SystemTools tools = new SystemTools(command -> {
            git.add(command.subList(3, command.size()));
            return command.get(3).equals("rev-parse") ? Optional.empty() : Optional.of("");
        }, command -> 0, Map.of(), "Linux");

        assertThat(tools.initGit(work)).isEmpty();
        assertThat(git).containsExactly(List.of("rev-parse", "--is-inside-work-tree"), List.of("init", "-q"),
                List.of("symbolic-ref", "HEAD", "refs/heads/main"), List.of("add", "-A"),
                List.of("commit", "-q", "-m", "Initial commit from blueprint new"));
    }

    @Test
    void leavesNoRepositoryBehindWhenTheCommitFails() throws Exception {
        Files.createDirectories(work.resolve(".git/objects"));
        SystemTools tools = new SystemTools(command -> command.get(3).equals("commit") || command.get(3).equals("rev-parse")
                ? Optional.empty() : Optional.of(""), command -> 0, Map.of(), "Linux");

        assertThat(tools.initGit(work)).hasValueSatisfying(reason -> assertThat(reason).contains("user.name"));
        assertThat(work.resolve(".git")).doesNotExist();
    }

    @Test
    void readsJavaAndGitVersionsInEveryKnownForm() {
        assertThat(SystemTools.javaMajor("21.0.2")).isEqualTo(21);
        assertThat(SystemTools.javaMajor("25")).isEqualTo(25);
        assertThat(SystemTools.javaMajor("1.8.0_292")).isEqualTo(8);
        SystemTools apple = new SystemTools(command -> Optional.of("git version 2.39.3 (Apple Git-145)\n"),
                command -> 0, Map.of(), "Mac OS X");
        assertThat(apple.gitVersion()).contains("2.39.3");
        SystemTools openJdk = new SystemTools(command -> Optional.of("openjdk version \"25\" 2025-09-16\n"),
                command -> 0, Map.of(), "Linux");
        assertThat(openJdk.javaVersion()).contains("25");
    }

    /** A machine with the given Java and git, Gradle or not, and installers that record instead of running. */
    private SystemTools tools(Optional<String> java, Optional<String> git, boolean gradle) throws Exception {
        Path bin = Files.createDirectories(work.resolve("bin"));
        for (String program : gradle ? List.of("jbang", "bash", "curl", "gradle") : List.of("jbang", "bash", "curl")) {
            Path file = Files.writeString(bin.resolve(program), "");
            assertThat(file.toFile().setExecutable(true)).isTrue();
        }
        return new SystemTools(command -> switch (command.get(0)) {
            case "java" -> java.map(version -> "openjdk version \"" + version + "\"");
            case "git" -> git.map(version -> "git version " + version);
            default -> Optional.empty();
        }, command -> {
            List<String> recorded = new ArrayList<>(command);
            if (command.get(0).equals("jbang")) {
                recorded.set(0, "/jbang");
            } else {
                recorded.set(2, command.get(2).replaceAll(".*&& ", ""));
            }
            installed.add(recorded);
            return 0;
        }, Map.of("PATH", bin.toString()), "Linux");
    }

    private String run(SystemTools tools, String stdin) {
        StringWriter out = new StringWriter();
        CommandLine cli = BlueprintCli.commandLine(TEMPLATE, new BufferedReader(new StringReader(stdin)), RELEASES, tools);
        cli.setOut(new PrintWriter(out, true));
        cli.setErr(new PrintWriter(out, true));
        int exitCode = cli.execute("new", "--parent-dir", work.toString());
        assertThat(exitCode).as(out.toString()).isZero();
        return out.toString();
    }
}
