package com.acme.blueprint.shared.testing;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * The one database container every integration test runs against — the only
 * test code that names the database vendor. Spring Boot connects the
 * DataSource and Flyway to it ({@link ServiceConnection}).
 *
 * <p>The image comes from {@code database-image} in {@code libs.versions.toml},
 * which the build passes as a system property. The container runs the same
 * privilege script as {@code compose.yaml}, so migrations can create the
 * module users.
 */
@TestConfiguration(proxyBeanMethods = false)
@ImportTestcontainers
@Import({CacheTestcontainers.class, BrokerTestcontainers.class, StorageTestcontainers.class})
public class DatabaseTestcontainers {

    private static final String IMAGE_PROPERTY = "blueprint.database-image";
    private static final String PRIVILEGES = "oracle-init/01-privileges.sql";

    /** One container for every test context in this JVM: contexts are cached, and each would start its own. */
    @ServiceConnection
    static final OracleContainer DATABASE = new OracleContainer(imageName())
            .withUsername("blueprint")
            .withPassword("blueprint")
            .withCopyFileToContainer(MountableFile.forClasspathResource(PRIVILEGES),
                        "/container-entrypoint-initdb.d/01-privileges.sql");

    private static DockerImageName imageName() {
        String image = System.getProperty(IMAGE_PROPERTY);
        if (image == null) {
            throw new IllegalStateException(
                    "System property '" + IMAGE_PROPERTY + "' is not set — run tests through Gradle.");
        }
        return DockerImageName.parse(image).asCompatibleSubstituteFor("gvenzl/oracle-free");
    }
}
