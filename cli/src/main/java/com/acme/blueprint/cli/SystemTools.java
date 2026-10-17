package com.acme.blueprint.cli;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Java, git and Gradle on this machine: which versions are there, and the
 * commands that install what is missing. It decides nothing and prints
 * nothing — {@link NewCommand} holds the conversation.
 */
final class SystemTools {

    /** Gradle 9 needs Java 17+ to start; the project's own JDK then comes through the toolchain. */
    static final int MIN_JAVA = 17;
    static final int MIN_GIT = 2;

    // `java -version`: openjdk version "21.0.2" — "25" for a .0 release, "1.8.0_292" before Java 9.
    private static final Pattern JAVA_VERSION = Pattern.compile("version \"([0-9][0-9._]*)\"");
    // `git --version`: git version 2.43.0 — or 2.39.3 (Apple Git-145), 2.45.1.windows.1.
    private static final Pattern GIT_VERSION = Pattern.compile("git version (\\d+(?:\\.\\d+)*)");

    /** Runs a command and returns what it printed; empty when it cannot start or fails. */
    private final Function<List<String>, Optional<String>> probe;
    /** Runs a command on the user's terminal, so sudo and installers can ask; returns its exit code. */
    private final Function<List<String>, Integer> run;
    private final Map<String, String> environment;
    private final boolean windows;
    private final boolean mac;

    SystemTools(Function<List<String>, Optional<String>> probe, Function<List<String>, Integer> run,
            Map<String, String> environment, String osName) {
        this.probe = probe;
        this.run = run;
        this.environment = environment;
        String os = osName.toLowerCase(Locale.ROOT);
        this.windows = os.contains("win");
        this.mac = os.contains("mac");
    }

    static SystemTools local() {
        return new SystemTools(SystemTools::capture, SystemTools::attached, System.getenv(), System.getProperty("os.name"));
    }

    /** The Java ./gradlew would use: {@code JAVA_HOME}'s, else the one on the PATH. */
    Optional<String> javaVersion() {
        String javaHome = environment.get("JAVA_HOME");
        String java = javaHome != null && !javaHome.isBlank() ? Path.of(javaHome, "bin", "java").toString() : "java";
        return probe.apply(List.of(java, "-version")).flatMap(output -> first(JAVA_VERSION, output));
    }

    /** The feature release: 21 for "21.0.2", 8 for "1.8.0_292". */
    static int javaMajor(String version) {
        String[] parts = version.split("[._]");
        return Integer.parseInt(parts[0].equals("1") && parts.length > 1 ? parts[1] : parts[0]);
    }

    Optional<String> gitVersion() {
        return probe.apply(List.of("git", "--version")).flatMap(output -> first(GIT_VERSION, output));
    }

    static int gitMajor(String version) {
        return Integer.parseInt(version.split("\\.")[0]);
    }

    boolean hasGradle() {
        return onPath("gradle");
    }

    /** JBang — which runs this CLI — installs the JDK and puts it on the PATH; empty without JBang. */
    List<List<String>> javaInstall(String version) {
        return jbang().map(jbang -> List.of(
                List.of(jbang, "jdk", "install", version),
                List.of(jbang, "jdk", "default", version),
                List.of(jbang, "app", "setup", "--java"))).orElse(List.of());
    }

    /** The system's package manager; empty when none is known. */
    List<List<String>> gitInstall() {
        if (windows) {
            return onPath("winget") ? List.of(List.of("winget", "install", "--id", "Git.Git", "-e")) : List.of();
        }
        if (onPath("brew")) {
            return List.of(List.of("brew", "install", "git"));
        }
        if (mac) {
            return List.of(List.of("xcode-select", "--install"));
        }
        for (List<String> manager : List.of(
                List.of("apt-get", "install", "-y", "git"),
                List.of("dnf", "install", "-y", "git"),
                List.of("pacman", "-S", "--noconfirm", "git"),
                List.of("zypper", "install", "-y", "git"))) {
            if (onPath(manager.get(0))) {
                return List.of(Stream.concat(Stream.of("sudo"), manager.stream()).toList());
            }
        }
        return List.of();
    }

    /** SDKMAN — installed first when missing — installs Gradle and puts it on the PATH; not on Windows. */
    List<List<String>> gradleInstall(String version) {
        if (windows || !onPath("bash") || !onPath("curl")) {
            return List.of();
        }
        return List.of(List.of("bash", "-c",
                "[ -d \"$HOME/.sdkman\" ] || curl -fsSL 'https://get.sdkman.io?rcupdate=true' | bash"
                        + " && . \"$HOME/.sdkman/bin/sdkman-init.sh\" && sdk install gradle " + version));
    }

    /** Runs the commands in order, stopping at the first that fails. */
    boolean install(List<List<String>> commands) {
        return commands.stream().allMatch(command -> run.apply(command) == 0);
    }

    private Optional<String> jbang() {
        if (onPath("jbang")) {
            return Optional.of("jbang");
        }
        Path home = Path.of(environment.getOrDefault("HOME", System.getProperty("user.home")), ".jbang", "bin", "jbang");
        return Files.isExecutable(home) ? Optional.of(home.toString()) : Optional.empty();
    }

    private boolean onPath(String program) {
        String path = environment.getOrDefault("PATH", environment.getOrDefault("Path", ""));
        return Arrays.stream(path.split(File.pathSeparator))
                .filter(directory -> !directory.isBlank())
                .flatMap(directory -> windows
                        ? Stream.of(Path.of(directory, program + ".exe"), Path.of(directory, program + ".cmd"))
                        : Stream.of(Path.of(directory, program)))
                .anyMatch(Files::isExecutable);
    }

    private static Optional<String> first(Pattern pattern, String text) {
        Matcher match = pattern.matcher(text);
        return match.find() ? Optional.of(match.group(1)) : Optional.empty();
    }

    private static Optional<String> capture(List<String> command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            process.getOutputStream().close();
            String output;
            try (InputStream stream = process.getInputStream()) {
                output = new String(stream.readAllBytes(), UTF_8);
            }
            boolean finished = process.waitFor(20, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0 ? Optional.of(output) : Optional.empty();
        } catch (IOException cannotStart) {
            return Optional.empty();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private static int attached(List<String> command) {
        try {
            return new ProcessBuilder(command).inheritIO().start().waitFor();
        } catch (IOException cannotStart) {
            return -1;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }
}
