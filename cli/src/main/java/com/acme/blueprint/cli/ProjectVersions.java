package com.acme.blueprint.cli;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * The versions a project builds with — Java, Spring Boot and Gradle — and the
 * two files that pin them. Spring Boot's BOM brings Hibernate and the rest.
 *
 * @param gradleSha256 the checksum the wrapper verifies the Gradle download against
 */
record ProjectVersions(String java, String springBoot, String gradle, String gradleSha256) {

    static final String CATALOG = "gradle/libs.versions.toml";
    static final String WRAPPER = "gradle/wrapper/gradle-wrapper.properties";

    private static final Pattern JAVA = Pattern.compile("(?m)^java = \"([^\"]+)\"");
    private static final Pattern SPRING_BOOT = Pattern.compile("(?m)^spring-boot = \"([^\"]+)\"");
    private static final Pattern GRADLE = Pattern.compile("gradle-([0-9][0-9.]*)-bin\\.zip");
    static final String DOCKERFILE = "Dockerfile";
    static final String CI_WORKFLOW = ".github/workflows/build.yml";
    private static final Pattern TEMURIN = Pattern.compile("eclipse-temurin:(\\d+)-");
    private static final Pattern CI_JAVA = Pattern.compile("java-version: \"(\\d+)\"");
    private static final Pattern GRADLE_SHA256 = Pattern.compile("(?m)^distributionSha256Sum=(\\w+)");

    /** The template's own versions; empty when it does not pin them (a test template). */
    static Optional<ProjectVersions> of(ProjectTemplate template) {
        Map<String, String> files = new HashMap<>();
        try (InputStream archive = template.archive().get(); ZipInputStream zip = new ZipInputStream(archive)) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (entry.getName().equals(CATALOG) || entry.getName().equals(WRAPPER)) {
                    files.put(entry.getName(), new String(zip.readAllBytes(), UTF_8));
                }
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        if (files.size() < 2) {
            return Optional.empty();
        }
        String catalog = files.get(CATALOG);
        String wrapper = files.get(WRAPPER);
        return Optional.of(new ProjectVersions(find(JAVA, catalog), find(SPRING_BOOT, catalog),
                find(GRADLE, wrapper), find(GRADLE_SHA256, wrapper)));
    }

    /** The file at {@code path} pinned to these versions; any other file comes back unchanged. */
    String pin(String path, String text) {
        return switch (path) {
            case CATALOG -> replace(replace(text, JAVA, java), SPRING_BOOT, springBoot);
            case WRAPPER -> replace(replace(text, GRADLE, gradle), GRADLE_SHA256, gradleSha256);
            case DOCKERFILE -> replace(text, TEMURIN, java);
            case CI_WORKFLOW -> replace(text, CI_JAVA, java);
            default -> text;
        };
    }

    private static String find(Pattern pattern, String text) {
        Matcher match = pattern.matcher(text);
        if (!match.find()) {
            throw new ProjectGenerationException("The template does not pin a version matching " + pattern + ".");
        }
        return match.group(1);
    }

    /** Replaces the first group of every match, keeping the rest of the match. */
    private static String replace(String text, Pattern pattern, String value) {
        Matcher match = pattern.matcher(text);
        StringBuilder result = new StringBuilder();
        while (match.find()) {
            String replaced = text.substring(match.start(), match.start(1)) + value + text.substring(match.end(1), match.end());
            match.appendReplacement(result, Matcher.quoteReplacement(replaced));
        }
        return match.appendTail(result).toString();
    }
}
