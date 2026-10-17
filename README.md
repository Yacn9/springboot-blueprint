# Modular Monolith Blueprint

A starting point for a **Spring Boot 4.1 + Hibernate 7** backend built as a
**modular monolith** with **Gradle 9** — one deployable, many isolated business
modules, boundaries enforced by the build itself.

| | |
| --- | --- |
| Language | Java 25 (LTS), Gradle toolchain |
| Framework | Spring Boot 4.1.1, Spring Modulith 2.1.1 |
| Persistence | Hibernate 7 via Spring Data JPA, Flyway, PostgreSQL 18 |
| Build | Gradle 9.8.1, Kotlin DSL, convention plugins in `build-logic/`, version catalog |
| Tests | JUnit 6, Spring Modulith test, Testcontainers 2, ArchUnit |
| API | REST + JSON, RFC 9457 problem details, OpenAPI via springdoc |

Read **[docs/architecture.md](docs/architecture.md)** before adding code — it
explains the rules the build enforces and why.

---

## Quick start

Prerequisites: a JDK 17+ to launch Gradle (Gradle provisions JDK 25 itself) and
Docker (for the database and the integration tests).

```bash
./gradlew build            # compile, unit + module + architecture + end-to-end tests
./gradlew :app:bootRun     # starts PostgreSQL from compose.yaml, then the app on :8080
```

Then open <http://localhost:8080/swagger-ui.html>, or:

```bash
curl -X POST localhost:8080/api/customers -H 'Content-Type: application/json' -d '{
  "nationalCode": "0012345679",
  "profile": { "firstName": "Ali", "lastName": "Rezaei", "mobile": "09121234567",
               "email": "ali@example.com", "tier": "GOLD", "branch": "Tehran Central" } }'

curl "localhost:8080/api/notifications?customerId=<id from the response>"   # the welcome message
```

| Endpoint | Purpose |
| --- | --- |
| `GET/POST /api/customers`, `GET /api/customers/{id}` | List (search, status, tier, page, size, `sort=createdAt\|lastName`), register, read |
| `PUT /api/customers/{id}/profile`, `PUT /api/customers/{id}/status` | Edit profile, change lifecycle status — send `If-Match` with the `ETag` you read |
| `GET /api/notifications?customerId=`, `GET /api/notifications/{id}`, `POST /api/notifications` | Read (paged, newest first) and send notifications |
| `/actuator/health`, `/actuator/modulith` | Health, runtime module graph |

Every error is an RFC 9457 problem detail with a stable `code` — see
"Errors" in [docs/architecture.md](docs/architecture.md).

---

## Layout

```
app/                     the only bootable project — wires modules together at runtime
shared/                  zero-domain building blocks: envelope, errors, Clock, event registry schema
modules/customers/api    :customers-api      public contract (plain Java)
modules/customers/impl   :customers-impl     domain · application · infrastructure · web
modules/notifications/…  :notifications-api / :notifications-impl
build-logic/             convention plugins every project applies
gradle/libs.versions.toml
```

`customers` is the full reference slice — copy its shape. `notifications`
shows both ways modules talk: it reacts to `CustomerRegistered` events and
calls `CustomerDirectory` synchronously.

## Useful tasks

```bash
./gradlew :customers-impl:test                 # one module, in isolation
./gradlew :app:test --tests '*ModularityTests' # boundaries + regenerate module diagrams
                                               # → app/build/spring-modulith-docs/
./gradlew :app:bootJar                         # the deployable → app/build/libs/
```

## Generating a new project

One command, no clone — it asks for the project name and renames the package,
the Gradle project and the application class for you (needs Java 17+):

```bash
bash <(curl -Ls https://raw.githubusercontent.com/Yacn9/springboot-blueprint/main/install.sh)
```

The tool lives in [`cli/`](cli/README.md), which also explains its options and
how it is released.

## Making it yours

1. Rename the base package `com.acme.blueprint` (all projects) and
   `rootProject.name` in `settings.gradle.kts`.
2. Replace or delete `customers` / `notifications`; follow
   "Adding a New Module" in [docs/architecture.md](docs/architecture.md).
3. Replace `LoggingNotificationSender` with a real channel.
4. Production config: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`,
   `SPRING_DATASOURCE_PASSWORD`.

Not included, on purpose: security, event externalization (Kafka/AMQP) and
observability. Each fits behind the existing seams — a `web`-level security
filter chain in `:app`, `@Externalized` events, `spring-modulith-starter-insight`.

## Troubleshooting

**Gradle cannot download JDK 25** ("Unable to download toolchain … foojay"):
the toolchain is fetched from GitHub release assets, which some networks
block. Install any JDK 25 yourself and either put it in a standard location
or point Gradle at it in your *user-level* `~/.gradle/gradle.properties`
(not the project's):

```properties
org.gradle.java.installations.paths=C:/path/to/jdk-25
```
