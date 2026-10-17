package com.acme.blueprint.cli;

import java.util.Arrays;
import java.util.List;

/**
 * The message broker the modules' events are also published to — none keeps
 * them inside the application. Kafka and Redpanda share the Kafka client and
 * Spring Modulith's Kafka externalizer; they differ in the server: the compose
 * service and the test container (overlays {@code broker/} and
 * {@code broker-<id>/}).
 */
enum BrokerProvider {

    NONE("none", null, null),
    KAFKA("kafka", "testcontainers-kafka", """
              kafka:
                image: apache/kafka:4.1.0
                ports:
                  - "9092:9092"   # fixed: the broker tells clients to connect to localhost:9092
                environment:
                  KAFKA_NODE_ID: 1
                  KAFKA_PROCESS_ROLES: broker,controller
                  KAFKA_LISTENERS: HOST://:9092,DOCKER://:19092,CONTROLLER://:9093
                  KAFKA_ADVERTISED_LISTENERS: HOST://localhost:9092,DOCKER://kafka:19092
                  KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: HOST:PLAINTEXT,DOCKER:PLAINTEXT,CONTROLLER:PLAINTEXT
                  KAFKA_INTER_BROKER_LISTENER_NAME: DOCKER
                  KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
                  KAFKA_CONTROLLER_QUORUM_VOTERS: 1@localhost:9093
                  KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
                  KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
                  KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
            """),
    REDPANDA("redpanda", "testcontainers-redpanda", """
              redpanda:
                image: redpandadata/redpanda:v24.3.1
                command:
                  - redpanda
                  - start
                  - --mode=dev-container
                  - --smp=1
                  - --kafka-addr=internal://0.0.0.0:19092,external://0.0.0.0:9092
                  - --advertise-kafka-addr=internal://redpanda:19092,external://localhost:9092
                ports:
                  - "9092:9092"   # fixed: the broker tells clients to connect to localhost:9092
            """);

    private static final String SHARED_BUILD = "shared/build.gradle.kts";
    private static final String TEST_DATABASE = "    \"testDatabaseImplementation\"(libs.database.testcontainers)\n";
    private static final String SHARED_WEB = "    implementation(\"org.springframework.boot:spring-boot-starter-webmvc\")\n";

    private final String id;
    private final String testcontainersModule;
    private final String composeService;

    BrokerProvider(String id, String testcontainersModule, String composeService) {
        this.id = id;
        this.testcontainersModule = testcontainersModule;
        this.composeService = composeService;
    }

    static List<String> ids() {
        return Arrays.stream(values()).map(provider -> provider.id).toList();
    }

    static BrokerProvider of(String id) {
        return Arrays.stream(values()).filter(provider -> provider.id.equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown broker: " + id));
    }

    String id() {
        return id;
    }

    List<String> overlays() {
        return this == NONE ? List.of() : List.of(Database.OVERLAYS + "broker/", Database.OVERLAYS + "broker-" + id + "/");
    }

    /** The service name in compose.yaml, which the application container reaches on port 19092. */
    String composeName() {
        return id;
    }

    String pin(String path, String text) {
        if (this == NONE) {
            return text;
        }
        return switch (path) {
            case SHARED_BUILD -> text
                    .replace(SHARED_WEB, SHARED_WEB
                            + "    // The modules' events, published to " + id + " too (EventExternalization in :app).\n"
                            + "    implementation(\"org.springframework.boot:spring-boot-starter-kafka\")\n"
                            + "    implementation(\"org.springframework.modulith:spring-modulith-events-kafka\")\n")
                    .replace(TEST_DATABASE, TEST_DATABASE + "    \"testDatabaseImplementation\"(\"org.testcontainers:" + testcontainersModule + "\")\n");
            case Extras.APP_BUILD -> text
                    .replace("    implementation(\"org.springframework.modulith:spring-modulith-api\")\n",
                            "    implementation(\"org.springframework.modulith:spring-modulith-api\")\n"
                                    + "    implementation(\"org.springframework.modulith:spring-modulith-events-api\")\n")
                    .replace("    testImplementation(\"org.awaitility:awaitility\")\n",
                            "    testImplementation(\"org.awaitility:awaitility\")\n"
                                    + "    testImplementation(\"org.springframework.boot:spring-boot-starter-kafka\")\n");
            case CacheProvider.COMPOSE -> text + composeService;
            default -> text;
        };
    }
}
