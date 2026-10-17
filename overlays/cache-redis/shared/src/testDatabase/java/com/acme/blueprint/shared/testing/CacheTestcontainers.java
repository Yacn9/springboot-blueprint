package com.acme.blueprint.shared.testing;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The cache server the integration tests run against, imported with
 * {@link DatabaseTestcontainers}: Redis, which Spring Boot connects to
 * ({@link ServiceConnection}). Keep the image in step with compose.yaml.
 */
@TestConfiguration(proxyBeanMethods = false)
public class CacheTestcontainers {

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> cacheContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:8-alpine")).withExposedPorts(6379);
    }
}
