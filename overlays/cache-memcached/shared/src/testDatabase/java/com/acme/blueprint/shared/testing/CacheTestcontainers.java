package com.acme.blueprint.shared.testing;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The cache server the integration tests run against, imported with
 * {@link DatabaseTestcontainers}: Memcached. Spring Boot cannot connect to it
 * by itself, so its address is handed to the adapter's property. One container
 * for every test context in this JVM. Keep the image in step with compose.yaml.
 */
@TestConfiguration(proxyBeanMethods = false)
@ImportTestcontainers
public class CacheTestcontainers {

    private static final int PORT = 11211;

    static final GenericContainer<?> CACHE = new GenericContainer<>(DockerImageName.parse("memcached:1.6-alpine")).withExposedPorts(PORT);

    @DynamicPropertySource
    static void memcachedServers(DynamicPropertyRegistry registry) {
        registry.add("blueprint.cache.memcached.servers", () -> {
            CACHE.start();   // a no-op once running
            return CACHE.getHost() + ":" + CACHE.getMappedPort(PORT);
        });
    }
}
