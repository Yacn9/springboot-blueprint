# blueprint — Spring Boot modular monoliths in one command

```bash
curl -fsSL https://raw.githubusercontent.com/Yacn9/springboot-blueprint/main/install.sh | sh
```

Like `npm create vite` for Spring Boot. A few questions, then a complete,
building project: one deployable, many isolated business modules, with the
module boundaries enforced by the build itself — not by good intentions.

Lists are menus: ↑/↓ to move, Enter to pick — Space ticks the scans. What is
coming but not there yet is shown greyed out, with *(soon)*.

```text
? Project name (for example my-shop): order-hub
? How do you want to set up the project? Custom
? Java version: 25
? Spring Boot version: 4.1.1
? Gradle version: 9.8.1
? Database: PostgreSQL
? Cache: Redis
? Message broker: Kafka
? Sample modules: Both
? Sample modules' tests: Both
? Docker Compose for the local database and cache (bootRun starts it)? [Y/n]:
? Deploy: Kubernetes
? A GitHub Actions workflow that builds and tests every push? [Y/n]:
? Secure the API with JWTs (OAuth2 resource server)? [y/N]: y
? Observability: Prometheus + OpenTelemetry
? Dependabot for dependency updates? [Y/n]:
? OWASP security scans in CI: CodeQL, ZAP baseline
? Initialize a git repository with a first commit? [Y/n]:

Checking tools:
  java    25.0.1
  git     2.47.0
  gradle  not found (optional: projects run their own ./gradlew)

Created order-hub in ./order-hub (130 files, base package com.orderhub, PostgreSQL, cache: redis).
```

```bash
cd order-hub
./gradlew build            # every test, against real containers
./gradlew :app:bootRun     # starts the database and cache, then the app on :8080
```

## What you choose

| | Choices | Default |
| --- | --- | --- |
| Java | the newest release and every LTS from 21, listed live by Adoptium (25 and 21 offline) | the installed one; else the newest LTS |
| Spring Boot, Gradle | the newest release of the template's line, looked up live — typed when it cannot be | newest |
| Database | PostgreSQL, or Oracle 23ai (one Oracle user per module) | PostgreSQL |
| Cache | none, in memory (Caffeine), Redis, or Memcached | none |
| Message broker | none, Kafka, Redpanda or RabbitMQ — the modules' events are published there too, after commit | none |
| Sample modules | both (`users` and `notifications`), `users` alone, or an empty project — then whose tests | both, with tests |
| Docker Compose | the local database and cache, started by `bootRun` | yes |
| Deploy | none, Docker (Dockerfile + the app in compose), or Kubernetes (plus a Helm chart) | none |
| Observability | none, or Prometheus + OpenTelemetry (Grafana LGTM, ELK and Jaeger coming) | Prometheus + OpenTelemetry |
| OWASP scans | any of CodeQL (SAST), Dependency-Check (SCA) and ZAP baseline (DAST) | none |
| Extras | GitHub Actions CI · JWT security · Dependabot | CI, Dependabot |
| Git | `git init` on `main` with *Initial commit from blueprint new* | yes |

Pick *Default* at the first question (or pass `--defaults`) and nothing else is asked:
every choice takes its default. Most questions also have a flag (`--modules users`,
`--scans`, `--no-observability`, …), and piped input is read as typed answers, so
scripts and CI need no menus:

```bash
curl -fsSL …/install.sh | sh -s -- order-hub --database oracle --cache redis --deploy kubernetes --security --no-git
```

Missing Java, git or Gradle? The CLI offers to install them — through JBang,
your package manager and SDKMAN — and installs nothing without a yes.

## What you get

- **Boundaries the compiler enforces.** Every module is an `-api` project (its
  public contract, plain Java) and an `-impl` project. Importing another
  module's internals does not compile; Spring Modulith and ArchUnit catch what
  Gradle cannot — cycles, wrong layering, SQL outside migrations.
- **A schema per module**, migrated by Flyway, validated by Hibernate, no
  foreign keys across modules — so a module can leave as a service later.
- **Modules that talk two ways:** transactional, at-least-once events through
  Spring Modulith's publication registry, and small synchronous `Directory`
  interfaces.
- **A REST API with care:** RFC 9457 problem details with stable codes,
  `ETag` / `If-Match` optimistic locking, paging and sorting by public keys,
  OpenAPI.
- **Tests at every level:** domain, use case, web slice, one module alone
  against a real database, the architecture, and end to end — all in
  `./gradlew build`, on Testcontainers.
- **Ready to ship, if you asked:** a non-root layered Docker image, a Helm chart
  with Spring Boot's probes, a CI workflow, JWT security and observability.

Try the sample API once it runs:

```bash
curl -X POST localhost:8080/api/users -H 'Content-Type: application/json' -d '{
  "nationalCode": "0012345679",
  "profile": { "firstName": "Vesen", "lastName": "Naqibi", "mobile": "09121234567",
               "email": "vesen@example.com", "tier": "GOLD", "branch": "Tehran Central" } }'

curl "localhost:8080/api/notifications?userId=<id from the response>"   # the welcome message
```

## Requirements

`curl` and `sh` (macOS, Linux, Git Bash on Windows). The installer adds
[JBang](https://www.jbang.dev), which runs the CLI. The generated project needs
Java 17+ to start Gradle — Gradle fetches the chosen JDK itself — and Docker for
its tests.

## Go deeper

- **[docs/architecture.md](docs/architecture.md)** — the rules every generated
  project follows, and why: modules, layers, events, persistence, REST, testing.
- **[cli/README.md](cli/README.md)** — how the CLI works: the install script,
  overlays, releases, developing it.

## Working on the template

This repository is the template itself, kept building and tested; the CLI
packs it into every project.

```bash
./gradlew build                               # the template's own tests (needs Docker)
./gradlew -p cli build                        # the CLI and its tests
java -jar cli/build/libs/blueprint-cli-*.jar new demo -d /tmp
```

`.github/workflows/ci.yml` generates a project per database, cache, deployment
and extra, and runs each one's full build against real containers.
