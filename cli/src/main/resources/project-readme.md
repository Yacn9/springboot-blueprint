# {{name}}

A **modular monolith** on Spring Boot 4.1, Hibernate 7 and Java 25 — one
deployable, many isolated business modules, with the module boundaries
enforced by the build itself. Base package: `{{package}}`.

Read **[docs/architecture.md](docs/architecture.md)** before adding code: it
explains the rules the build enforces and why.

## Quick start

Prerequisites: a JDK 17+ to launch Gradle (Gradle provisions JDK 25 itself)
and Docker (for the database and the integration tests).

```bash
./gradlew build            # compile, unit + module + architecture + end-to-end tests
./gradlew :app:bootRun     # starts {{database}} from compose.yaml, then the app on :8080
```

On Windows PowerShell or cmd, write `.\gradlew` instead of `./gradlew`.

Then open <http://localhost:8080/swagger-ui.html>.

## Layout

```
app/                     the only bootable project — wires modules together at runtime
shared/                  zero-domain building blocks: envelope, errors, Clock, event registry schema
modules/users/api    :users-api      public contract (plain Java)
modules/users/impl   :users-impl     domain · application · infrastructure · web
modules/notifications/…  :notifications-api / :notifications-impl
build-logic/             convention plugins every project applies
gradle/libs.versions.toml
```

`users` is the full reference slice — copy its shape. `notifications`
shows both ways modules talk: it reacts to `UserRegistered` events and
calls `UserDirectory` synchronously. Replace or delete both once you have
your own modules; "Adding a New Module" in the architecture document lists the
steps.

## Production configuration

`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`.

## Troubleshooting

**Gradle cannot download JDK 25** ("Unable to download toolchain … foojay"):
the toolchain is fetched from GitHub release assets, which some networks
block. Install any JDK 25 yourself and either put it in a standard location
or point Gradle at it in your *user-level* `~/.gradle/gradle.properties`
(not the project's):

```properties
org.gradle.java.installations.paths=C:/path/to/jdk-25
```
