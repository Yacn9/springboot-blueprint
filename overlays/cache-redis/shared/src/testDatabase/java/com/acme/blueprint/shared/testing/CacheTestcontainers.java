package com.acme.blueprint.shared.testing;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The cache server the integration tests run against, imported with
 * {@link DatabaseTestcontainers}: Redis, which Spring Boot connects to
 * ({@link ServiceConnection}). One container for every test context in this
 * JVM. Keep the image in step with compose.yaml.
 */
@TestConfiguration(proxyBeanMethods = false)
@ImportTestcontainers
public class CacheTestcontainers {

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> CACHE = new GenericContainer<>(DockerImageName.parse("redis:8-alpine")).withExposedPorts(6379);
}
