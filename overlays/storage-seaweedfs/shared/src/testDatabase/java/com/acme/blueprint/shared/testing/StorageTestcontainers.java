package com.acme.blueprint.shared.testing;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;

/**
 * The object storage the integration tests run against, imported with
 * {@link DatabaseTestcontainers}: SeaweedFS's S3 gateway, with one identity —
 * the credentials of the platform defaults, as in seaweedfs-s3.json. Spring
 * Boot cannot connect to it by itself, so its address is handed to
 * {@code blueprint.storage.endpoint}. One container for every test context in
 * this JVM. Keep the image and the flags in step with compose.yaml.
 */
@TestConfiguration(proxyBeanMethods = false)
@ImportTestcontainers
public class StorageTestcontainers {

    private static final int PORT = 8333;
    private static final String IDENTITIES = """
            {
              "identities": [
                {
                  "name": "blueprint",
                  "credentials": [{ "accessKey": "blueprint-storage", "secretKey": "blueprint-storage-secret" }],
                  "actions": ["Admin", "Read", "Write", "List", "Tagging"]
                }
              ]
            }
            """;

    static final GenericContainer<?> STORAGE = new GenericContainer<>(DockerImageName.parse("chrislusf/seaweedfs:4.48"))
            .withCopyToContainer(Transferable.of(IDENTITIES), "/etc/seaweedfs/s3.json")
            .withCommand("server", "-dir=/data", "-master.volumeSizeLimitMB=256", "-volume.max=10", "-filer", "-s3",
                    "-s3.config=/etc/seaweedfs/s3.json")
            .withExposedPorts(PORT)
            // Any answer means the gateway is serving; an anonymous request is refused, which is an answer too.
            .waitingFor(Wait.forHttp("/").forPort(PORT).forStatusCodeMatching(status -> status > 0));

    @DynamicPropertySource
    static void storageEndpoint(DynamicPropertyRegistry registry) {
        registry.add("blueprint.storage.endpoint", () -> {
            STORAGE.start();   // a no-op once running
            return "http://" + STORAGE.getHost() + ":" + STORAGE.getMappedPort(PORT);
        });
    }
}
