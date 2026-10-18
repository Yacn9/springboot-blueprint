package com.acme.blueprint.cli;

import java.util.Arrays;
import java.util.List;

/**
 * The message broker the modules' events are also published to — none keeps
 * them inside the application. Kafka and Redpanda share the Kafka client and
 * its overlay ({@code broker-kafka-protocol/}, a topic per event type) and
 * differ in the server; RabbitMQ has its own ({@code broker-rabbitmq/}, one
 * topic exchange routed by event type).
 */
enum BrokerProvider {

    NONE("none", null, null, null, null, List.of(), null),
    KAFKA("kafka", "spring-boot-starter-kafka", "spring-modulith-events-kafka", "testcontainers-kafka",
            "      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:19092\n", List.of("broker-kafka-protocol", "broker-kafka"), """
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
    REDPANDA("redpanda", "spring-boot-starter-kafka", "spring-modulith-events-kafka", "testcontainers-redpanda",
            "      SPRING_KAFKA_BOOTSTRAP_SERVERS: redpanda:19092\n", List.of("broker-kafka-protocol", "broker-redpanda"), """
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
            """),
    RABBITMQ("rabbitmq", "spring-boot-starter-amqp", "spring-modulith-events-amqp", "testcontainers-rabbitmq",
            "      SPRING_RABBITMQ_HOST: rabbitmq\n      SPRING_RABBITMQ_USERNAME: blueprint\n      SPRING_RABBITMQ_PASSWORD: blueprint\n",
            List.of("broker-rabbitmq"), """
              rabbitmq:
                image: rabbitmq:4-management-alpine
                environment:
                  # Its own user: RabbitMQ's guest account only accepts connections from localhost.
                  RABBITMQ_DEFAULT_USER: blueprint
                  RABBITMQ_DEFAULT_PASS: blueprint
                ports:
                  - "5672"
                  - "15672"   # the management UI: `docker compose port rabbitmq 15672` shows where
            """);

    private static final String SHARED_BUILD = "shared/build.gradle.kts";
    private static final String TEST_DATABASE = "    \"testDatabaseImplementation\"(libs.database.testcontainers)\n";
    private static final String SHARED_WEB = "    implementation(\"org.springframework.boot:spring-boot-starter-webmvc\")\n";
    private static final String MODULITH_API = "    implementation(\"org.springframework.modulith:spring-modulith-api\")\n";
    private static final String AWAITILITY = "    testImplementation(\"org.awaitility:awaitility\")\n";

    private final String id;
    private final String starter;
    private final String modulithModule;
    private final String testcontainersModule;
    private final String appEnvironment;
    private final List<String> overlays;
    private final String composeService;

    BrokerProvider(String id, String starter, String modulithModule, String testcontainersModule, String appEnvironment,
            List<String> overlays, String composeService) {
        this.id = id;
        this.starter = starter;
        this.modulithModule = modulithModule;
        this.testcontainersModule = testcontainersModule;
        this.appEnvironment = appEnvironment;
        this.overlays = overlays;
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

    /** As the menu shows it. */
    Choice choice() {
        return switch (this) {
            case NONE -> Choice.of(id, "None", "events stay inside the application");
            case KAFKA -> Choice.of(id, "Kafka");
            case REDPANDA -> Choice.of(id, "Redpanda", "Kafka-compatible, one binary");
            case RABBITMQ -> Choice.of(id, "RabbitMQ");
        };
    }

    List<String> overlays() {
        return overlays.stream().map(name -> Database.OVERLAYS + name + "/").toList();
    }

    /** The compose service name, which the application container depends on. */
    String composeName() {
        return id;
    }

    /** How the application container in compose.yaml reaches the broker. */
    String appEnvironment() {
        return appEnvironment;
    }

    String pin(String path, String text) {
        if (this == NONE) {
            return text;
        }
        String starterDependency = "\"org.springframework.boot:" + starter + "\"";
        return switch (path) {
            case SHARED_BUILD -> text
                    .replace(SHARED_WEB, SHARED_WEB
                            + "    // The modules' events, published to " + id + " too (EventExternalization in :app).\n"
                            + "    implementation(" + starterDependency + ")\n"
                            + "    implementation(\"org.springframework.modulith:" + modulithModule + "\")\n")
                    .replace(TEST_DATABASE, TEST_DATABASE + "    \"testDatabaseImplementation\"(\"org.testcontainers:" + testcontainersModule + "\")\n");
            // RabbitMQ's EventExternalization declares an exchange, so it compiles against Spring AMQP.
            case Extras.APP_BUILD -> text
                    .replace(MODULITH_API, MODULITH_API
                            + "    implementation(\"org.springframework.modulith:spring-modulith-events-api\")\n"
                            + (this == RABBITMQ ? "    implementation(" + starterDependency + ")\n" : ""))
                    .replace(AWAITILITY, AWAITILITY + (this == RABBITMQ ? "" : "    testImplementation(" + starterDependency + ")\n"));
            case CacheProvider.COMPOSE -> text + composeService;
            default -> text;
        };
    }
}
