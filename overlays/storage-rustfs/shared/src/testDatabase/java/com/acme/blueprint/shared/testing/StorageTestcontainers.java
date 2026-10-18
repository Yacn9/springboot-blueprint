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
 * {@link DatabaseTestcontainers}: RustFS, with the credentials of the platform
 * defaults. Spring Boot cannot connect to it by itself, so its address is
 * handed to {@code blueprint.storage.endpoint}. One container for every test
 * context in this JVM. Keep the image in step with compose.yaml.
 */
@TestConfiguration(proxyBeanMethods = false)
@ImportTestcontainers
public class StorageTestcontainers {

    private static final int PORT = 9000;

    static final GenericContainer<?> STORAGE = new GenericContainer<>(DockerImageName.parse("rustfs/rustfs:1.0.3"))
            .withEnv("RUSTFS_ACCESS_KEY", "blueprint-storage")
            .withEnv("RUSTFS_SECRET_KEY", "blueprint-storage-secret")
            .withExposedPorts(PORT)
            // Any answer means it is serving; an anonymous request is refused, which is an answer too.
            .waitingFor(Wait.forHttp("/").forPort(PORT).forStatusCodeMatching(status -> status > 0));

    @DynamicPropertySource
    static void storageEndpoint(DynamicPropertyRegistry registry) {
        registry.add("blueprint.storage.endpoint", () -> {
            STORAGE.start();   // a no-op once running
            return "http://" + STORAGE.getHost() + ":" + STORAGE.getMappedPort(PORT);
        });
    }
}
