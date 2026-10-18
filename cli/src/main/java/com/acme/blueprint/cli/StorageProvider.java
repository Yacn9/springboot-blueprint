package com.acme.blueprint.cli;

import java.util.Arrays;
import java.util.List;

/**
 * Where the project keeps files outside its database — none, or an
 * S3-compatible server. The code is the same for all: shared's
 * {@code ObjectStore} on an S3 client ({@code overlays/storage-s3/}), set up
 * by {@code blueprint.storage} in the platform defaults. A provider is the
 * server: its {@code compose.yaml} service, its test container
 * ({@code overlays/storage-<id>/}) and the port the application is pointed at.
 */
enum StorageProvider {

    NONE("none", 0, null),
    MINIO("minio", 9000, """
              minio:
                # MinIO stopped publishing images in 2025; this is Pigsty's rebuild of it.
                image: pgsty/minio:RELEASE.2026-08-04T00-00-00Z
                command: server /data --console-address :9001
                environment:
                  MINIO_ROOT_USER: blueprint-storage
                  MINIO_ROOT_PASSWORD: blueprint-storage-secret
                ports:
                  - "9000:9000"   # fixed: the application is pointed at localhost:9000
                  - "9001"        # the console: `docker compose port minio 9001` shows where
            """),
    SEAWEEDFS("seaweedfs", 8333, """
              seaweedfs:
                image: chrislusf/seaweedfs:4.48
                command: server -dir=/data -master.volumeSizeLimitMB=256 -volume.max=10 -filer -s3 -s3.config=/etc/seaweedfs/s3.json
                volumes:
                  - ./seaweedfs-s3.json:/etc/seaweedfs/s3.json:ro   # the application's credentials
                ports:
                  - "8333:8333"   # fixed: the application is pointed at localhost:8333
            """),
    RUSTFS("rustfs", 9000, """
              rustfs:
                image: rustfs/rustfs:1.0.3
                environment:
                  RUSTFS_ACCESS_KEY: blueprint-storage
                  RUSTFS_SECRET_KEY: blueprint-storage-secret
                  RUSTFS_CONSOLE_ENABLE: "true"
                ports:
                  - "9000:9000"   # fixed: the application is pointed at localhost:9000
                  - "9001"        # the console: `docker compose port rustfs 9001` shows where
            """);

    static final String S3_OVERLAY = "storage-s3";
    /** SeaweedFS's identities for compose.yaml; its test container carries its own copy. */
    static final String SEAWEEDFS_CONFIG = "seaweedfs-s3.json";
    private static final String S3_LIBRARY =
            "storage-s3 = { module = \"software.amazon.awssdk:s3\", version = \"2.55.8\" }";
    private static final String LIBRARIES = "[libraries]\n";
    private static final String SHARED_CACHE = "    implementation(libs.bundles.cache)\n";
    private static final String AWAITILITY = "    testImplementation(\"org.awaitility:awaitility\")\n";
    private static final String PLATFORM_ROOT = "\nblueprint:\n";

    private final String id;
    private final int port;
    private final String composeService;

    StorageProvider(String id, int port, String composeService) {
        this.id = id;
        this.port = port;
        this.composeService = composeService;
    }

    static List<String> ids() {
        return Arrays.stream(values()).map(provider -> provider.id).toList();
    }

    static StorageProvider of(String id) {
        return Arrays.stream(values()).filter(provider -> provider.id.equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown storage: " + id));
    }

    String id() {
        return id;
    }

    /** As the menu shows it. */
    Choice choice() {
        return switch (this) {
            case NONE -> Choice.of(id, "None", "no files outside the database");
            case MINIO -> Choice.of(id, "MinIO", "Pigsty's rebuild: MinIO publishes no images since 2025");
            case SEAWEEDFS -> Choice.of(id, "SeaweedFS", "S3 gateway in front of its own file system");
            case RUSTFS -> Choice.of(id, "RustFS", "MinIO-compatible, Apache-2.0");
        };
    }

    List<String> overlays() {
        return this == NONE ? List.of()
                : List.of(Database.OVERLAYS + S3_OVERLAY + "/", Database.OVERLAYS + "storage-" + id + "/");
    }

    /** The compose service name, which the application container depends on. */
    String composeName() {
        return id;
    }

    /** How the application container in compose.yaml reaches the storage. */
    String appEnvironment() {
        return "      BLUEPRINT_STORAGE_ENDPOINT: http://" + id + ":" + port + "\n";
    }

    /** The file at {@code path} set up for this provider; any other file unchanged. */
    String pin(String path, String text) {
        if (this == NONE) {
            return text;
        }
        return switch (path) {
            case ProjectVersions.CATALOG -> text.replace(LIBRARIES, LIBRARIES
                    + "# Object storage: the S3 client behind shared's ObjectStore.\n" + S3_LIBRARY + "\n");
            case BrokerProvider.SHARED_BUILD -> text.replace(SHARED_CACHE, SHARED_CACHE
                    + "    // Object storage: an S3 client behind ObjectStore (see blueprint.storage in the platform defaults).\n"
                    + "    implementation(libs.storage.s3)\n");
            // ObjectStoreTests autowires shared's ObjectStore.
            case Extras.APP_BUILD -> text.replace(AWAITILITY, AWAITILITY + "    testImplementation(project(\":shared\"))\n");
            case CacheProvider.PLATFORM -> platform(text);
            case CacheProvider.COMPOSE -> text + composeService;
            default -> text;
        };
    }

    /** blueprint.storage, under the platform defaults' {@code blueprint:} root when a cache adapter already has one. */
    private String platform(String text) {
        String settings = """
                  # Object storage: any S3-compatible server — %s in compose.yaml. In production,
                  # point it at yours with BLUEPRINT_STORAGE_ENDPOINT, _ACCESS_KEY, _SECRET_KEY.
                  storage:
                    endpoint: http://localhost:%d
                    region: us-east-1
                    access-key: blueprint-storage
                    secret-key: blueprint-storage-secret
                    bucket: blueprint-files
                """.formatted(choice().label(), port);
        return text.contains(PLATFORM_ROOT)
                ? text.replace(PLATFORM_ROOT, PLATFORM_ROOT + settings)
                : text + PLATFORM_ROOT + settings;
    }
}
