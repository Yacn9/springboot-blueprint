package com.acme.blueprint.shared.testing;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * A throwaway database for integration tests; {@code @ServiceConnection}
 * points the DataSource at it. Every module test and :app's end-to-end test
 * import it with {@code @Import(DatabaseTestcontainers.class)}. Its Testcontainers
 * module is not on consumers' compile classpath, so this is the only test code
 * that can name a database vendor.
 */
@TestConfiguration(proxyBeanMethods = false)
public class DatabaseTestcontainers {

    /** Set by the Gradle test task from {@code database-image} in libs.versions.toml. */
    private static final String IMAGE_PROPERTY = "blueprint.database-image";

    @Bean
    @ServiceConnection
    PostgreSQLContainer databaseContainer() {
        return new PostgreSQLContainer(imageName());
    }

    /**
     * Declared a substitute for the vendor's official image, so a registry
     * mirror's name ({@code mirror.example.com/postgres:18-alpine}) works too.
     */
    private static DockerImageName imageName() {
        String image = System.getProperty(IMAGE_PROPERTY);
        if (image == null) {
            throw new IllegalStateException(
                    "System property '" + IMAGE_PROPERTY + "' is not set — run tests through Gradle.");
        }
        return DockerImageName.parse(image).asCompatibleSubstituteFor("postgres");
    }
}
