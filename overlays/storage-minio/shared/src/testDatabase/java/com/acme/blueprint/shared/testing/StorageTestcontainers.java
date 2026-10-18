package com.acme.blueprint.shared.testing;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * The object storage the integration tests run against, imported with
 * {@link DatabaseTestcontainers}: MinIO, with the credentials of the platform
 * defaults. Spring Boot cannot connect to it by itself, so its address is
 * handed to {@code blueprint.storage.endpoint}. One container for every test
 * context in this JVM. Keep the image in step with compose.yaml.
 */
@TestConfiguration(proxyBeanMethods = false)
@ImportTestcontainers
public class StorageTestcontainers {

    private static final int PORT = 9000;

    // MinIO stopped publishing images in 2025; this is Pigsty's rebuild of it.
    static final GenericContainer<?> STORAGE = new GenericContainer<>(DockerImageName.parse("pgsty/minio:RELEASE.2026-08-04T00-00-00Z"))
            .withCommand("server", "/data")
            .withEnv("MINIO_ROOT_USER", "blueprint-storage")
            .withEnv("MINIO_ROOT_PASSWORD", "blueprint-storage-secret")
            .withExposedPorts(PORT)
            .waitingFor(Wait.forHttp("/minio/health/live").forPort(PORT));

    @DynamicPropertySource
    static void storageEndpoint(DynamicPropertyRegistry registry) {
        registry.add("blueprint.storage.endpoint", () -> {
            STORAGE.start();   // a no-op once running
            return "http://" + STORAGE.getHost() + ":" + STORAGE.getMappedPort(PORT);
        });
    }
}
