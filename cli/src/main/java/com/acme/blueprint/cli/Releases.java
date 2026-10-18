package com.acme.blueprint.cli;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
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

    /** Java LTS releases the template compiles on, newest first: what is offered when Adoptium cannot be asked. */
    static final List<String> JAVA_LTS = List.of("25", "21");
    /** The oldest Java the template's code compiles on. */
    static final int MIN_JAVA = 21;

    private static final String JAVA_RELEASES = "https://api.adoptium.net/v3/info/available_releases";
    private static final Pattern JAVA_LTS_RELEASES = Pattern.compile("\"available_lts_releases\"\\s*:\\s*\\[([\\d,\\s]*)]");
    private static final Pattern JAVA_LATEST = Pattern.compile("\"most_recent_feature_release\"\\s*:\\s*(\\d+)");

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

    /** A Java feature release, and whether it is a long-term-support one. */
    record Java(int feature, boolean lts) {
    }

    /**
     * Java releases a project can build on, newest first: the most recent
     * feature release and every LTS since {@link #MIN_JAVA}, as Adoptium lists
     * them — or the known LTS releases when Adoptium cannot be asked.
     */
    List<Java> java() {
        Optional<String> listing = fetch.apply(JAVA_RELEASES);
        Optional<Matcher> lts = listing.map(JAVA_LTS_RELEASES::matcher).filter(Matcher::find);
        Optional<Matcher> latest = listing.map(JAVA_LATEST::matcher).filter(Matcher::find);
        if (lts.isEmpty() || latest.isEmpty()) {
            return JAVA_LTS.stream().map(feature -> new Java(Integer.parseInt(feature), true)).toList();
        }
        List<Integer> ltsReleases = Arrays.stream(lts.get().group(1).split(","))
                .map(String::strip).filter(feature -> !feature.isEmpty()).map(Integer::parseInt).toList();
        int newest = Integer.parseInt(latest.get().group(1));
        List<Java> releases = new ArrayList<>();
        if (!ltsReleases.contains(newest)) {
            releases.add(new Java(newest, false));
        }
        ltsReleases.stream().filter(feature -> feature >= MIN_JAVA).sorted(Comparator.reverseOrder())
                .forEach(feature -> releases.add(new Java(feature, true)));
        return releases;
    }

    /**
     * Spring Boot releases of the template's major line, no older than the
     * template's, newest first — empty when Maven Central cannot be asked.
     */
    Optional<List<String>> springBoot(String templateVersion) {
        return fetch.apply(SPRING_BOOT_METADATA).map(metadata -> {
            List<String> found = new ArrayList<>();
            Matcher match = RELEASE_VERSION.matcher(metadata);
            while (match.find()) {
                found.add(match.group(1));
            }
            return newestFirst(found, templateVersion);
        });
    }

    /**
     * The current Gradle release when it is of the template's major line and
     * newer, then the template's — empty when services.gradle.org cannot be asked.
     */
    Optional<List<String>> gradle(String templateVersion) {
        return fetch.apply(GRADLE_CURRENT).map(current -> {
            List<String> found = new ArrayList<>();
            Matcher match = GRADLE_VERSION.matcher(current);
            if (match.find()) {
                found.add(match.group(1));
            }
            return newestFirst(found, templateVersion);
        });
    }

    /**
     * A version typed in place of a list: of the template's major line and no
     * older than the template's.
     *
     * @throws IllegalArgumentException with what would do, when it is not
     */
    static String sameLine(String typed, String templateVersion, String what) {
        if (!typed.matches("\\d+\\.\\d+(\\.\\d+)?") || major(typed) != major(templateVersion)
                || compare(typed, templateVersion) < 0) {
            throw new IllegalArgumentException("Enter %s %s or a newer %d.x release.".formatted(what, templateVersion,
                    major(templateVersion)));
        }
        return typed;
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
