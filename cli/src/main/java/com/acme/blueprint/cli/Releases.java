package com.acme.blueprint.cli;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The versions a new project can choose from: the template's own, plus newer
 * releases of the same major line when the internet answers. A newer major
 * (Spring Boot 5, Gradle 10) is never offered — the template is not built for it.
 */
final class Releases {

    /**
     * Java LTS releases the template compiles on, newest first.
     * ponytail: fixed list; add the next LTS (29, September 2027) once the template builds on it.
     */
    static final List<String> JAVA_LTS = List.of("25", "21");

    private static final String SPRING_BOOT_METADATA =
            "https://repo1.maven.org/maven2/org/springframework/boot/spring-boot/maven-metadata.xml";
    private static final String GRADLE_CURRENT = "https://services.gradle.org/versions/current";
    private static final String GRADLE_DISTRIBUTIONS = "https://services.gradle.org/distributions/";
    private static final Pattern RELEASE_VERSION = Pattern.compile("<version>(\\d+\\.\\d+\\.\\d+)</version>");
    private static final Pattern GRADLE_VERSION = Pattern.compile("\"version\"\\s*:\\s*\"(\\d+\\.\\d+(?:\\.\\d+)?)\"");
    private static final int MAX_CHOICES = 3;

    /** Fetches a URL's body; empty when it cannot be reached. */
    private final Function<String, Optional<String>> fetch;

    Releases(Function<String, Optional<String>> fetch) {
        this.fetch = fetch;
    }

    /** Over the internet — or, with {@code BLUEPRINT_OFFLINE} set, only the template's versions. */
    static Releases online() {
        if (System.getenv("BLUEPRINT_OFFLINE") != null) {
            return new Releases(url -> Optional.empty());
        }
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        return new Releases(url -> {
            try {
                HttpResponse<String> response = http.send(
                        HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).build(),
                        HttpResponse.BodyHandlers.ofString());
                return response.statusCode() == 200 ? Optional.of(response.body()) : Optional.empty();
            } catch (java.io.IOException failure) {
                return Optional.empty();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        });
    }

    /** Spring Boot releases of the template's major line, no older than the template's, newest first. */
    List<String> springBoot(String templateVersion) {
        List<String> found = new ArrayList<>();
        fetch.apply(SPRING_BOOT_METADATA).ifPresent(metadata -> {
            Matcher match = RELEASE_VERSION.matcher(metadata);
            while (match.find()) {
                found.add(match.group(1));
            }
        });
        return newestFirst(found, templateVersion);
    }

    /** The current Gradle release when it is of the template's major line and newer, then the template's. */
    List<String> gradle(String templateVersion) {
        List<String> found = new ArrayList<>();
        fetch.apply(GRADLE_CURRENT).map(GRADLE_VERSION::matcher).filter(Matcher::find)
                .ifPresent(match -> found.add(match.group(1)));
        return newestFirst(found, templateVersion);
    }

    /** The checksum Gradle publishes for a distribution; empty when it cannot be fetched. */
    Optional<String> gradleSha256(String version) {
        return fetch.apply(GRADLE_DISTRIBUTIONS + "gradle-" + version + "-bin.zip.sha256")
                .map(String::strip)
                .filter(sha -> sha.matches("[0-9a-f]{64}"));
    }

    private static List<String> newestFirst(List<String> found, String templateVersion) {
        List<String> choices = new ArrayList<>(found.stream()
                .filter(version -> major(version) == major(templateVersion))
                .filter(version -> compare(version, templateVersion) >= 0)
                .distinct()
                .sorted(Comparator.comparing((String version) -> version, Releases::compare).reversed())
                .limit(MAX_CHOICES)
                .toList());
        if (!choices.contains(templateVersion)) {
            choices.add(templateVersion);
        }
        return choices;
    }

    private static int major(String version) {
        return Integer.parseInt(version.split("\\.")[0]);
    }

    /** Compares dotted numeric versions; a missing part counts as 0, so 9.8 equals 9.8.0. */
    static int compare(String left, String right) {
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");
        for (int index = 0; index < Math.max(a.length, b.length); index++) {
            int difference = Integer.compare(part(a, index), part(b, index));
            if (difference != 0) {
                return difference;
            }
        }
        return 0;
    }

    private static int part(String[] parts, int index) {
        return index < parts.length ? Integer.parseInt(parts[index]) : 0;
    }
}
