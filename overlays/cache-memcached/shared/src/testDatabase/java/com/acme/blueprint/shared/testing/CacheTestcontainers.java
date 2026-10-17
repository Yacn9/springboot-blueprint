package com.acme.blueprint.shared.testing;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The cache server the integration tests run against, imported with
 * {@link DatabaseTestcontainers}: Memcached. Spring Boot cannot connect to it
 * by itself, so its address is handed to the adapter's property. Keep the
 * image in step with compose.yaml.
 */
@TestConfiguration(proxyBeanMethods = false)
public class CacheTestcontainers {

    private static final int PORT = 11211;

    @Bean
    GenericContainer<?> cacheContainer() {
        return new GenericContainer<>(DockerImageName.parse("memcached:1.6-alpine")).withExposedPorts(PORT);
    }

    @Bean
    DynamicPropertyRegistrar memcachedServers(GenericContainer<?> cacheContainer) {
        return registry -> registry.add("blueprint.cache.memcached.servers",
                () -> cacheContainer.getHost() + ":" + cacheContainer.getMappedPort(PORT));
    }
}
