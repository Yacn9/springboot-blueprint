package com.acme.blueprint.cli;

/**
 * The edits the deployment and the extras make to template files — the
 * application's build and configuration, compose.yaml, the README — on top of
 * their overlays. Each anchor is template text, checked against the real
 * template by its tests.
 */
final class Extras {

    static final String APP_BUILD = "app/build.gradle.kts";
    static final String APP_CONFIG = "app/src/main/resources/application.yml";
    static final String DEPENDABOT = ".github/dependabot.yml";
    static final String HELM_VALUES = "deploy/helm/modular-monolith-blueprint/values.yaml";
    private static final String HELM_DATASOURCE_URL = "jdbc:postgresql://database:5432/blueprint";

    private static final String DOCKER_COMPOSE_DEPENDENCY = """
                // Starts compose.yaml (the database) on bootRun and wires the DataSource to it.
                developmentOnly("org.springframework.boot:spring-boot-docker-compose")
            """;
    private static final String MODULITH_API = "    implementation(\"org.springframework.modulith:spring-modulith-api\")\n";
    private static final String ACTUATOR = "    runtimeOnly(\"org.springframework.boot:spring-boot-starter-actuator\")\n";
    private static final String EXPOSURE = "include: health, info, modulith";
    private static final String APPLICATION_NAME = "  application:\n    name: modular-monolith-blueprint\n";

    private Extras() {
    }

    static String pin(ProjectSpec spec, String path, String text) {
        return switch (path) {
            case APP_BUILD -> appBuild(spec, text);
            case APP_CONFIG -> appConfig(spec, text);
            case CacheProvider.COMPOSE -> spec.has(Extra.COMPOSE) && spec.deploy().containerised()
                    ? text + appService(spec) : text;
            case DEPENDABOT -> dependabot(spec, text);
            case HELM_VALUES -> text.replace(HELM_DATASOURCE_URL, spec.database().jdbcUrl("database"));
            default -> text;
        };
    }

    private static String appBuild(ProjectSpec spec, String text) {
        String build = text;
        if (!spec.has(Extra.COMPOSE)) {
            build = build.replace(DOCKER_COMPOSE_DEPENDENCY, "");
        }
        if (spec.has(Extra.SECURITY)) {
            build = build.replace(MODULITH_API, MODULITH_API
                    + "    implementation(\"org.springframework.boot:spring-boot-starter-oauth2-resource-server\")\n"
                    + "    compileOnly(\"jakarta.servlet:jakarta.servlet-api\")   // SecurityConfiguration's error responses\n")
                    .replace("    testImplementation(\"org.awaitility:awaitility\")\n",
                            "    testImplementation(\"org.awaitility:awaitility\")\n"
                                    + "    testImplementation(\"org.springframework.boot:spring-boot-starter-security-test\")\n");
        }
        if (spec.has(Extra.OBSERVABILITY)) {
            build = build.replace(ACTUATOR, ACTUATOR
                    + "    runtimeOnly(\"org.springframework.boot:spring-boot-starter-opentelemetry\")\n"
                    + "    runtimeOnly(\"io.micrometer:micrometer-registry-prometheus\")\n");
        }
        return build;
    }

    private static String appConfig(ProjectSpec spec, String text) {
        String config = text;
        if (spec.has(Extra.OBSERVABILITY)) {
            config = config.replace(EXPOSURE, EXPOSURE + ", prometheus");
        }
        if (spec.has(Extra.SECURITY)) {
            config = config.replace(APPLICATION_NAME, APPLICATION_NAME + """

                      # Access tokens are checked against your identity provider's signing keys
                      # (Keycloak, Auth0, Entra ID, …), fetched on the first request carrying one.
                      security:
                        oauth2:
                          resourceserver:
                            jwt:
                              jwk-set-uri: ${JWT_JWK_SET_URI:http://localhost:8180/realms/blueprint/protocol/openid-connect/certs}
                    """);
        }
        return config;
    }

    /** The application itself in compose.yaml, under a profile so bootRun does not start it. */
    private static String appService(ProjectSpec spec) {
        Database database = spec.database();
        StringBuilder service = new StringBuilder("""
                  # The application, built from the Dockerfile:
                  #   docker compose --profile app up --build
                  # ./gradlew :app:bootRun does not start it: it runs the application from source.
                  app:
                    profiles: ["app"]
                    build: .
                    restart: on-failure   # until the database accepts connections
                    ports:
                      - "8080:8080"
                    environment:
                """);
        service.append("      SPRING_DATASOURCE_URL: ").append(database.jdbcUrl(database.composeService())).append('\n')
                .append("      SPRING_DATASOURCE_USERNAME: blueprint\n")
                .append("      SPRING_DATASOURCE_PASSWORD: blueprint\n");
        switch (spec.cache()) {
            case REDIS -> service.append("      SPRING_DATA_REDIS_HOST: redis\n");
            case MEMCACHED -> service.append("      BLUEPRINT_CACHE_MEMCACHED_SERVERS: memcached:11211\n");
            default -> { }
        }
        if (spec.broker() != BrokerProvider.NONE) {
            service.append("      SPRING_KAFKA_BOOTSTRAP_SERVERS: ").append(spec.broker().composeName()).append(":19092\n");
        }
        service.append("    depends_on:\n      - ").append(database.composeService()).append('\n');
        if (spec.cache() == CacheProvider.REDIS || spec.cache() == CacheProvider.MEMCACHED) {
            service.append("      - ").append(spec.cache().id()).append('\n');
        }
        if (spec.broker() != BrokerProvider.NONE) {
            service.append("      - ").append(spec.broker().composeName()).append('\n');
        }
        return service.toString();
    }

    private static String dependabot(ProjectSpec spec, String text) {
        String updates = text;
        if (spec.has(Extra.CI)) {
            updates += ecosystem("github-actions");
        }
        if (spec.deploy().containerised()) {
            updates += ecosystem("docker");
        }
        return updates;
    }

    private static String ecosystem(String name) {
        return "  - package-ecosystem: " + name + "\n    directory: /\n    schedule:\n      interval: weekly\n";
    }

    /** The generated README: how to run without compose, and how to ship. */
    static String readme(ProjectSpec spec, String text) {
        String readme = text;
        if (!spec.has(Extra.COMPOSE)) {
            readme = readme.replace("# starts " + spec.database().displayName() + " from compose.yaml, then the app on :8080",
                    "# the app on :8080 — set SPRING_DATASOURCE_URL, _USERNAME, _PASSWORD first");
        }
        StringBuilder more = new StringBuilder();
        if (spec.deploy().containerised()) {
            more.append("\n## Container image\n\n```bash\ndocker build -t ").append(spec.artifact()).append(" .\n");
            if (spec.has(Extra.COMPOSE)) {
                more.append("docker compose --profile app up --build   # the image with its database (and cache)\n");
            }
            more.append("```\n");
        }
        if (spec.deploy() == Deployment.KUBERNETES) {
            more.append("\n## Kubernetes\n\nPush the image, then install the Helm chart:\n\n```bash\n")
                    .append("helm install ").append(spec.artifact()).append(" deploy/helm/").append(spec.artifact())
                    .append(" \\\n  --set image.repository=<registry>/").append(spec.artifact())
                    .append(" \\\n  --set datasource.url=<jdbc url> --set datasource.password=<password>\n```\n\n")
                    .append("Probes use Spring Boot's liveness and readiness groups. The database runs outside the cluster.\n");
        }
        if (spec.has(Extra.SECURITY)) {
            more.append("\n## Security\n\nEvery endpoint except health, info and the OpenAPI documents needs a JWT\n")
                    .append("(`Authorization: Bearer …`) signed by the keys at `JWT_JWK_SET_URI`.\n");
        }
        if (spec.has(Extra.OBSERVABILITY)) {
            more.append("\n## Observability\n\nMetrics at `/actuator/prometheus`; traces through OpenTelemetry (OTLP) —\n")
                    .append("point them at a collector with Spring Boot's OTLP export properties.\n");
        }
        return readme + more;
    }
}
