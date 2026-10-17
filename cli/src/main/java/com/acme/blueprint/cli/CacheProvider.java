package com.acme.blueprint.cli;

import java.util.Arrays;
import java.util.List;

/**
 * What holds the project's cache entries. The code is the same for all —
 * modules cache with Spring's annotations — so a provider is the version
 * catalog's {@code cache} bundle, the {@code spring.cache} block of the
 * platform defaults, a {@code compose.yaml} service, and for a server an
 * overlay ({@code overlays/cache-<id>/}) with its test container.
 */
enum CacheProvider {

    NONE("none", null, null, null, null),
    MEMORY("memory",
            "cache-caffeine = { module = \"com.github.ben-manes.caffeine:caffeine\" }",
            """
              cache:
                type: caffeine
                caffeine:
                  spec: maximumSize=10000,expireAfterWrite=10m
            """, null, null),
    REDIS("redis",
            "cache-redis = { module = \"org.springframework.boot:spring-boot-starter-data-redis\" }",
            """
              cache:
                type: redis
                redis:
                  time-to-live: 10m
            """, null, """
              redis:
                image: redis:8-alpine
                ports:
                  - "6379"
            """),
    MEMCACHED("memcached",
            "cache-memcached = { module = \"com.googlecode.xmemcached:xmemcached\", version = \"2.4.9\" }",
            """
              # Held in Memcached: see blueprint.cache.memcached at the end of this file.
            """, """

            # Memcached, through shared's MemcachedCacheConfiguration: Spring Boot has
            # no Memcached support of its own.
            blueprint:
              cache:
                memcached:
                  servers: localhost:11211
                  expiry: 10m
            """, """
              memcached:
                image: memcached:1.6-alpine
                ports:
                  # Fixed, unlike the others: Spring Boot cannot point the adapter at a random port.
                  - "11211:11211"
            """);

    static final String PLATFORM = "shared/src/main/resources/config/blueprint-platform.yml";
    static final String COMPOSE = "compose.yaml";
    private static final String TEMPLATE_BUNDLE = "cache = []";
    private static final String TEMPLATE_SPRING_CACHE = "  cache:\n    type: none\n";
    private static final String LIBRARIES = "[libraries]\n";

    private final String id;
    private final String library;
    private final String springCache;
    private final String platformTail;
    private final String composeService;

    CacheProvider(String id, String library, String springCache, String platformTail, String composeService) {
        this.id = id;
        this.library = library;
        this.springCache = springCache;
        this.platformTail = platformTail;
        this.composeService = composeService;
    }

    static List<String> ids() {
        return Arrays.stream(values()).map(provider -> provider.id).toList();
    }

    static CacheProvider of(String id) {
        return Arrays.stream(values()).filter(provider -> provider.id.equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown cache: " + id));
    }

    String id() {
        return id;
    }

    /** Where this provider's overlay lives in the template archive: {@code overlays/cache-redis/}. */
    String overlay() {
        return Database.OVERLAYS + "cache-" + id + "/";
    }

    /** The file at {@code path} set up for this provider; any other file unchanged. */
    String pin(String path, String text) {
        if (this == NONE) {
            return text;
        }
        return switch (path) {
            case ProjectVersions.CATALOG -> text
                    .replace(TEMPLATE_BUNDLE, "cache = [\"" + library.substring(0, library.indexOf(' ')) + "\"]")
                    .replace(LIBRARIES, LIBRARIES + library + "\n");
            case PLATFORM -> text.replace(TEMPLATE_SPRING_CACHE, springCache) + (platformTail != null ? platformTail : "");
            case COMPOSE -> composeService != null ? text + composeService : text;
            default -> text;
        };
    }
}
