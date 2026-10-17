# Modular Monolith Architecture

> Backend blueprint — Spring Boot 4.1 · Hibernate 7 · Spring Modulith 2.1 · Gradle 9 · Java 25

---

## Core Philosophy

This project is a **modular monolith**: one deployable Spring Boot application
whose internals are divided into isolated, domain-driven modules. Each module
is a **bounded context** — it owns its code, its database schema and its
REST endpoints, and talks to the rest of the system only through an explicit
contract: its `-api` Gradle project.

The goal is the benefit of a service-per-domain split — clear ownership,
encapsulation, independent evolution, a cheap path to extracting a service
later — without the operational cost of a distributed system.

This is a starting point, not a product. `users` is built out end to end
as the pattern to copy; `notifications` is a second, smaller module that exists
to show how modules talk to each other.

---

## Project Structure

```
.
├── app/                              # Composition root — NO business code
│   └── src/
│       ├── main/java/…/BlueprintApplication.java
│       ├── main/resources/application.yml      # deployment-specific settings only
│       └── test/                     # Whole-system tests (see Boundary Enforcement)
│
├── modules/                          # Bounded contexts
│   └── <module>/
│       ├── api/                      # :<module>-api  ← PUBLIC CONTRACT (plain Java)
│       │   └── com.acme.blueprint.<module>
│       │       ├── <Module>Directory.java   # synchronous query interface
│       │       ├── <Module>Id.java          # typed ids that cross the boundary
│       │       └── <Something>Happened.java # events (records)
│       └── impl/                     # :<module>-impl ← everything else (internal)
│           ├── src/main/java/com.acme.blueprint.<module>
│           │   ├── domain/           # aggregates, value objects, repository ports
│           │   ├── application/      # use cases, listeners, outbound + query ports
│           │   ├── infrastructure/   # JPA adapters, external-system adapters
│           │   └── web/              # REST controllers + request/response records
│           └── src/main/resources/db/migration/<module>/   # this module's Flyway scripts
│
├── shared/                           # :shared — zero domain knowledge
│   ├── com.acme.blueprint.shared     # ApiResponse, PageResponse, SortMapping, EntityTags, ConstraintViolations, error hierarchy
│   │   ├── web/                      # (internal) ProblemDetail exception handler
│   │   ├── config/                   # (internal) loads the platform defaults below
│   │   └── time/                     # (internal) Clock bean
│   ├── resources/config/blueprint-platform.yml    # platform defaults (JPA, Flyway, events…)
│   ├── testDatabase/                 # DatabaseTestcontainers — the one test container (a feature variant)
│   └── testFixtures/                 # ModuleTestApplication (+ exposes testDatabase)
│
├── build-logic/                      # Convention plugins (included build)
│   └── src/main/kotlin/
│       ├── blueprint.java-conventions.gradle.kts
│       ├── blueprint.module-api.gradle.kts
│       ├── blueprint.module-impl.gradle.kts
│       ├── blueprint.spring-boot-application.gradle.kts
│       └── ModuleDependencyRules.kt  # the configuration-time dependency guard
│
├── gradle/libs.versions.toml         # Every version, in one place
└── compose.yaml                      # Local PostgreSQL for bootRun
```

A module's two Gradle projects share one Java package root. The `-api` project
contributes the **base package** (`com.acme.blueprint.users`); the `-impl`
project contributes only **sub-packages** (`…users.domain`, `…users.web`,
…). That is exactly Spring Modulith's convention — base package = API,
sub-packages = internal — so Gradle and Modulith agree on where the boundary
is.

---

## The Golden Rule

```
<module>-impl  ──compiles against──▶  other modules' -api projects  +  :shared
       ▲
       └── :app pulls it in at runtime only
```

```java
// ✅ notifications-impl — through the contract
import com.acme.blueprint.users.UserDirectory;

// ❌ does not compile — users-impl is not on notifications' classpath
import com.acme.blueprint.users.domain.User;
```

If a type is not in a module's `-api` project, it does not exist to the rest
of the system.

---

## Dependency Rules

| Project          | May depend on                                         | Enforced by |
| ---------------- | ----------------------------------------------------- | ----------- |
| `:app`           | every `-impl` — **runtime only**                      | `app/build.gradle.kts` |
| `:<module>-impl` | its own `-api`, other modules' `-api`, `:shared`      | `blueprint.module-impl` guard |
| `:<module>-api`  | other modules' `-api` (rare), the JDK — no frameworks | `blueprint.module-api` guard + ArchUnit |
| `:shared`        | Spring / JPA platform libraries — never a module      | guard in `shared/build.gradle.kts` |

Module-to-module dependencies must form a directed acyclic graph. Today:

```
notifications ──▶ users
```

If `users` ever needs something from `notifications`, the arrow must not be
reversed by a direct call. Publish an event from `users` instead, or move
the shared concept into a new module both depend on.

### Inside a module

```
web ───────────┐
               ▼
infrastructure ──▶ application ──▶ domain
```

| Layer            | Contains                                               | May use                     |
| ---------------- | ------------------------------------------------------ | --------------------------- |
| `domain`         | Aggregates (JPA entities), value objects, repository ports, domain exceptions | the JDK, `jakarta.persistence`, `:shared`, its own and consumed `-api` projects (ids, value types) — **no Spring, Hibernate or Jackson** |
| `application`    | Use-case services, event listeners, outbound ports, query ports | `domain`, Spring (transactions, events, `Page`/`Pageable`) — **no JPA or Hibernate** |
| `infrastructure` | Spring Data repositories, port adapters (persistence, email, SMS…) | `application`, `domain`, anything technical |
| `web`            | `@RestController`s, request/response records           | `application`, `domain` (read-only — see below) |

Nothing depends on `web` or `infrastructure`. Persistence implements ports
declared further in (dependency inversion), so swapping JPA for something else
touches `infrastructure` plus the mapping annotations in `domain`.

Use cases return live aggregates for the web layer to render, but by then the
transaction is over: a mutation made in a controller would show in the
response and never be saved. ArchUnit therefore forbids `web` from calling
`void` methods on entities.

---

## How Modules Communicate

| Need                                         | Use                         | Example |
| -------------------------------------------- | --------------------------- | ------- |
| "Something happened, react if you care"      | **Event** (record in `-api`) | `UserRegistered` → notifications sends a welcome |
| "I need current data to serve this request"  | **Directory interface** in `-api` | `UserDirectory.findById` when sending an ad-hoc notification |

Prefer events. They keep the publisher ignorant of its consumers and let the
consumer fail and retry without affecting the publisher.

Contracts stay minimal: a type moves into `-api` only when another module
needs it. `UserStatus` and `UserTier` are internal to users;
`NotificationId` is internal to notifications.

### Events are transactional and at-least-once

```
UserManagement.register()  ── one transaction ─────────────────────────┐
  ├─ INSERT users.user_account   (flushed: uniqueness checked right here)  │
  ├─ publishEvent(UserRegistered)                                      │
  └─ INSERT event_publication (one row per listener)   ← Spring Modulith   │
COMMIT ────────────────────────────────────────────────────────────────────┘
        │
        ▼  async, new transaction
UserRegisteredListener.on()  (@ApplicationModuleListener)
  └─ success → publication completed (deleted, see completion-mode)
     failure → publication stays incomplete and can be resubmitted
```

- An event is delivered **if and only if** the publishing transaction commits.
- A listener may see the same event **more than once**. Listeners must be
  idempotent — `NotificationManagement.sendWelcome` checks first, and a
  partial unique index rejects a racing duplicate when the row is flushed,
  *before* anything is sent; the losing delivery is retried later as a no-op.
- Side effects outside the database are **at-least-once** too: if the commit
  fails after a message left, a retry sends it again. Pass the notification id
  to the provider as an idempotency key; for channels that must never repeat,
  or are slow, store the notification unsent and deliver it from an
  after-commit listener (see `NotificationSender`).
- Incomplete publications are not retried automatically by default. Resubmit
  them with `IncompleteEventPublications` (e.g. from a scheduled job), or set
  `spring.modulith.events.republish-outstanding-events-on-restart=true` on a
  single-instance deployment.
- Events carry the data listeners usually need (event-carried state transfer),
  so most listeners never call back into the publishing module.

---

## Persistence

- **One PostgreSQL database, one schema per module.** `users.user_account`,
  `notifications.notification`. A module never reads another module's schema.
- **No cross-module foreign keys or joins.** Modules reference each other's data
  by id (`notification.user_id`). This is what keeps a module extractable.
- **Flyway owns the schema; Hibernate validates it** (`ddl-auto: validate`) —
  in production and in every module test. The setting lives in `:shared`'s
  `config/blueprint-platform.yml`, which `PlatformDefaultsEnvironmentPostProcessor`
  adds to every Spring application on the classpath with the *lowest*
  precedence: `:app`'s `application.yml`, profiles, environment variables and
  test properties can override any platform value, and no module can lose the
  defaults by adding a config file of its own. Each module ships its scripts
  in its own jar under `db/migration/<module>/`; Flyway scans all of them.
- **Timestamp versions** — `V2026_10_17_1000__create_user.sql` — so two
  modules developed in parallel never claim the same version number.
- **Aggregates are JPA entities** with private fields, a protected no-arg
  constructor for Hibernate, intention-revealing methods and no setters.
  Embeddable **records** (`UserProfile`) model immutable value objects.
  This is a deliberate trade-off: one model instead of a domain model plus a
  mirrored persistence model. The cost — `jakarta.persistence` annotations in
  `domain` — is accepted; Spring is still forbidden there.
- **The domain is the single source of every rule.** Value objects and
  aggregates validate themselves (`NationalCode`, `UserProfile`,
  `Notification`) and publish their limits as constants; web DTOs reference
  those constants in their bean-validation annotations, so HTTP clients fail
  fast with every bad field listed while other entry points are still
  checked. Hibernate rebuilds embeddable records through their constructor on
  load, so **tightening a rule requires migrating existing data first**.
- **Assigned UUID ids + `@Version`.** The id is set in the domain; the `null`
  version tells Spring Data the entity is new, so `save` issues an `INSERT`
  without a prior `SELECT`. Aggregate ids are always typed (`UserId`,
  `NotificationId`); the type sits in `-api` only once it crosses a module
  boundary.
- **`add` writes immediately.** Repository ports insert new aggregates with a
  flush, so database constraints fire inside the use case — before events
  are published or messages sent — and adapters translate violations into
  business exceptions (`user_national_code_uk` →
  `DuplicateNationalCodeException`). Changes to loaded aggregates are written
  at commit (unit of work).
- `open-in-view` is off. Use cases load what the response needs.
- The event publication registry (`event_publication`) is platform
  infrastructure and is migrated from `:shared`.

### Database vendor

A project is generated for PostgreSQL or Oracle (`blueprint new` asks). On
Oracle, which has no schema apart from a user, each module's migration creates
an Oracle user that cannot log in (`users`, `notifications`) and its tables in
it; the application user needs `CREATE USER`, `CREATE ANY TABLE`,
`CREATE ANY INDEX` and the `… ANY TABLE` DML privileges — see
`oracle-init/01-privileges.sql`, which compose and the tests run. A partial
unique index becomes a unique function-based index.

In the template PostgreSQL is the vendor, and the code that names it is kept in a few
known places so that switching vendors is a contained change:

| Spot | Holds |
| ---- | ----- |
| `gradle/libs.versions.toml` | `database-image` and the `database-*` libraries (JDBC driver, Flyway module, Testcontainers module) |
| `DatabaseTestcontainers` (`:shared`, `testDatabase` feature) | The container type every integration test runs against. The vendor's Testcontainers module is not on consumers' compile classpath, so no other test can name it |
| `compose.yaml` | The local database for `bootRun` — its image is repeated by hand, keep it in step with `database-image` |
| `db/migration/**.sql` | All SQL |
| `README.md`, this document | Prose that says "PostgreSQL" |

**SQL lives only in the migrations.** Java queries through JPQL, the Criteria
API or derived Spring Data queries, which Hibernate renders for each dialect.
`ArchitectureRulesTests.sqlLivesOnlyInMigrations` rejects JDBC and Spring's JDBC
templates, native and stored-procedure queries, `CriteriaBuilder.function`
calls, and `@Column(columnDefinition = …)` in production code. It cannot read
query strings, so a vendor function written inside JPQL is a code-review matter.

**Where a database's own output reaches Java, `:shared` normalises it.**
`ConstraintViolations.isViolationOf` compares constraint names without any
schema qualifier and ignoring case, because PostgreSQL reports
`user_national_code_uk` and Oracle `USERS.USER_NATIONAL_CODE_UK`.
Adapters pass the name exactly as the migration declares it.

**The test image is a full image name.** `database-image` may point at a
registry mirror; `DatabaseTestcontainers` declares it a substitute for the
vendor's official image.

---

## REST Conventions

- Success bodies are wrapped: `{ "data": … }` (`ApiResponse`). **Every** list
  returns a page:
  `{ "data": { "items": [...], "page": 0, "size": 20, "totalItems": 42, "totalPages": 3 } }`
  (`PageResponse`); `page` is zero-based. Every list pages the same way:
  out-of-range values are clamped, never rejected (`page < 0` → 0,
  `size < 1` → the default, `size > 100` → 100).
- **Sorting uses public keys, not entity paths.** Each finder port declares
  its keys (`UserFinder.SORT_BY_CREATED_AT`, `SORT_BY_LAST_NAME`) and its
  persistence adapter maps them to entity paths with a `SortMapping` — next
  to the query, where module tests run them against the real schema. Every
  key is indexed, the id is appended as a tiebreaker so paging is stable, and
  any other key is a 400. Lists with a fixed order (notifications: newest
  first) declare no keys, so any `sort` is rejected rather than ignored.
- **Optimistic concurrency over HTTP.** Resources that accept `PUT` carry the
  aggregate version as an `ETag` (`"3"`) on every single-resource response.
  Their `PUT`s **require** `If-Match` with that value: missing → 428, stale →
  412 `request.version-mismatch` — whether the staleness is noticed when the
  aggregate is loaded or by `@Version` at commit. Weak tags (`W/"3"`, as
  compressing proxies produce) are accepted; `If-Match: *` is not.
  Immutable resources (notifications) carry no ETag.
- **Browser clients on another origin** can only read `ETag` and `Location`
  if CORS exposes them: when you configure CORS, include
  `Access-Control-Expose-Headers: ETag, Location`.
- Sub-resources for state that changes independently —
  `PUT /api/users/{id}/profile`, `PUT /api/users/{id}/status` — are
  write-only commands that return the updated parent.
- `POST` returns `201 Created` with a `Location` header (without the request's
  query string), plus an `ETag` for resources that accept `PUT`.
- OpenAPI: `/v3/api-docs`, Swagger UI: `/swagger-ui.html`.

### Errors

Every error — business, framework or unexpected — is an **RFC 9457 problem
detail** (`application/problem+json`), never wrapped, and always carries a
stable `code`. Clients translate by `code`; `detail` is for developers and may
change. Field-level problems also list `errors: [{ field, message }]`.

| Source | Status | Codes |
| ------ | ------ | ----- |
| `InvalidRequestException` | 400 | `request.invalid-sort`, `request.invalid-if-match` |
| Bean validation of a body or a query parameter, wrong JSON value type (e.g. an enum in the wrong case) | 400 | `request.invalid` + `errors` |
| Malformed JSON, bad path/query value, missing parameter or header | 400 | `request.malformed-body`, `request.invalid-parameter`, `request.missing-parameter` |
| Unknown route, method, media type | 404 / 405 / 415 / 406 | `request.not-found`, `request.method-not-allowed`, `request.unsupported-media-type`, `request.not-acceptable` |
| `ResourceNotFoundException` | 404 | `users.not-found`, `notifications.not-found` |
| `ConflictException` | 409 | `users.duplicate-national-code` |
| Concurrent commit (`@Version`) on a request without `If-Match` | 409 | `request.concurrent-modification` |
| `PreconditionFailedException` — stale `If-Match`, at load or at commit | 412 | `request.version-mismatch` |
| `PUT` without `If-Match` | 428 | `request.precondition-required` |
| `BusinessRuleViolationException` | 422 | `users.invalid-national-code`, `users.invalid-profile`, `users.closed`, `users.status-transition-not-allowed`, `notifications.unknown-user`, `notifications.invalid-content` |
| An exception annotated `@ResponseStatus` | its status | derived from the status |
| Anything else | 500 | `server.internal-error` (cause logged, never returned) |

When Spring Security is added, map `AccessDeniedException` (403) and
`AuthenticationException` (401) in the handler explicitly — they carry no
`@ResponseStatus` and would otherwise reach the 500 fallback.

`BusinessException` is **sealed** into one category per status, so the
handler maps them with an exhaustive `switch`; modules extend a category,
never the root. Codes must look like `area.some-name` — the constructor
rejects anything else, which catches swapped `(code, message)` arguments.

---

## Boundary Enforcement

Three independent layers, each catching what the previous one cannot:

| Layer | Tool | Catches | When |
| ----- | ---- | ------- | ---- |
| 1 | **Gradle** classpaths + `restrictProjectDependencies` (build-logic) | Any import of another module's internals; any `-impl` → `-impl`, `-api` → `-impl` or `:shared` → module dependency | Configuration / compile time |
| 2 | **Spring Modulith** `ApplicationModules.verify()` (`ModularityTests`) | Cycles between modules; access to internal packages that slipped past Gradle | `./gradlew test` |
| 3 | **ArchUnit** (`ArchitectureRulesTests`) | Wrong layer direction inside a module; anything but JDK/JPA/project types in `domain`; JPA or Hibernate in `application`; frameworks in an `-api`; entity mutation from `web`; controllers, entities or Spring Data repositories in the wrong package; JDBC or any SQL embedded in Java | `./gradlew test` |

`ModularityTests` also writes C4 / PlantUML component diagrams and a canvas per
module to `app/build/spring-modulith-docs/`.

Allowed module dependencies are declared **once**, in each `-impl`'s
`build.gradle.kts`. They are not repeated in `@ApplicationModule(allowedDependencies = …)`,
so the two can never drift apart.

---

## Testing

| Test | Scope | Where | Needs |
| ---- | ----- | ----- | ----- |
| Domain unit tests (`UserTest`, `UserProfileTest`) | One class, no Spring | `-impl` | — |
| Use-case tests (`UserManagementTest`) | Application layer with hand-written port fakes; asserts error codes | `-impl` | — |
| Web slice (`@WebMvcTest`) | Controller, validation, ETag/If-Match, error mapping; use cases mocked | `-impl` | — |
| Module test (`@ApplicationModuleTest`) | **One module alone** + `:shared`, real database, production platform settings, Modulith `Scenario` for events | `-impl` | Docker |
| Architecture (`ModularityTests`, `ArchitectureRulesTests`) | Whole codebase, static | `:app` | — |
| End to end (`BlueprintApplicationTests`) | All modules, all migrations, HTTP → event → other module | `:app` | Docker |

A module test runs with **only that module on the classpath** — its Gradle
project cannot even see the other implementations — so it proves the module
works in isolation. Collaborators from other modules are mocked through their
`-api` interfaces (`@MockitoBean UserDirectory`), and their events are
published by the test (`scenario.publish(new UserRegistered(…))`).

`ModuleTestApplication` (in `:shared`'s test fixtures) is the bootstrap every
module test finds; the platform defaults reach module tests exactly the way
they reach production. `:app` cannot see these fixtures —
two `@SpringBootConfiguration` classes in one package would be ambiguous — so
the container lives apart from them, in `:shared`'s `testDatabase` feature
variant: `DatabaseTestcontainers`, which module tests (through the fixtures)
and the end-to-end test (directly) both import. It reads the image from
`database-image` in `libs.versions.toml`.

Build-wide test settings (`blueprint.java-conventions`): Mockito runs as a JVM
agent rather than self-attaching (self-attach is deprecated and can deadlock a
JVM that already runs Testcontainers and pool threads), and every test task
times out after 10 minutes so a hang fails CI instead of blocking it.

---

## Adding a New Module

Say, `accounts`:

1. `settings.gradle.kts`: add `businessModule("accounts")`.
2. `modules/accounts/api/build.gradle.kts`:
   `plugins { id("blueprint.module-api") }`, plus
   `src/main/java/com/acme/blueprint/accounts/package-info.java` — even if the
   contract is empty for now.
3. `modules/accounts/impl/build.gradle.kts`:
   ```kotlin
   plugins { id("blueprint.module-impl") }
   dependencies {
       api(project(":accounts-api"))
       implementation(project(":users-api"))   // only the contracts you need
   }
   ```
4. Implement `domain/`, `application/`, `infrastructure/`, `web/` under
   `com.acme.blueprint.accounts`. Typed ids, validation in the domain,
   `add` that flushes, a `SortMapping` for every sortable list, `ETag` +
   `If-Match` for every `PUT`.
5. Add `src/main/resources/db/migration/accounts/V<yyyy_MM_dd_HHmm>__create_….sql`
   starting with `CREATE SCHEMA IF NOT EXISTS accounts;`, with an index per
   sort key.
6. `app/build.gradle.kts`: `runtimeOnly(project(":accounts-impl"))`.
7. Add an `AccountsModuleTests` with `@ApplicationModuleTest` and
   `@Import(DatabaseTestcontainers.class)`.
8. `./gradlew build` — the architecture tests will tell you if anything is off.

Never skip step 2. An empty contract is a deliberate statement that nothing
depends on this module yet.

---

## Anti-patterns to Avoid

| Anti-pattern | Why it's harmful |
| ------------ | ---------------- |
| `implementation(project(":other-impl"))` | Couples to internals; the build rejects it |
| Spring, JPA or Jackson types in an `-api` project | Leaks an implementation choice into every consumer |
| Returning an entity from a `-api` interface | Consumers start depending on your table layout — map to a summary record |
| Joining or FK-ing into another module's schema | Hidden coupling; the module can no longer be extracted or migrated alone |
| A non-idempotent `@ApplicationModuleListener` | Redelivery after a crash double-sends, double-charges, double-counts |
| A side effect before the row that guards it is flushed | The constraint fires at commit, after the message already left |
| Validation only in a web DTO | The next entry point (an import, a listener) skips it |
| Passing `Pageable` sort straight to JPA | Entity field names become public API; clients can sort by unindexed columns |
| A full-replace `PUT` without `If-Match` | Two editors silently overwrite each other |
| Business logic in a controller or a listener | Untestable without HTTP / events; duplicated when a second entry point appears |
| Setters on aggregates | Invariants can be bypassed; use intention-revealing methods |
| `Instant.now()` in application or domain code | Untestable time; inject `Clock` |
| Adding domain types to `:shared` | Every module silently depends on that domain |
| A cycle between modules "just this once" | `ModularityTests` fails — and it should; publish an event instead |

---

## References

The structure combines patterns from these sources:

- [Spring Modulith reference — Fundamentals](https://docs.spring.io/spring-modulith/reference/fundamentals.html):
  base package = API, sub-packages = internal, named interfaces, shared modules.
- [Spring Modulith reference — Appendix](https://docs.spring.io/spring-modulith/reference/appendix.html):
  starters, event-publication properties.
- [spring-projects/spring-modulith](https://github.com/spring-projects/spring-modulith):
  the v2 `event_publication` schema migrated in `:shared`.
- [kgrzybek/modular-monolith-with-ddd](https://github.com/kgrzybek/modular-monolith-with-ddd)
  (.NET): separate contract projects per module, schema per module,
  integration events, architecture tests.
- [ddd-by-examples/library](https://github.com/ddd-by-examples/library):
  hexagonal layering inside a module, ArchUnit rules, framework-free domain.
- [xsreality/spring-modulith-with-ddd](https://github.com/xsreality/spring-modulith-with-ddd):
  Spring Modulith + DDD on Spring Boot 4, eventual consistency via the
  publication registry.
- [Gradle — Structuring builds with convention plugins](https://docs.gradle.org/current/userguide/sharing_build_logic_between_subprojects.html)
  and [Java toolchains](https://docs.gradle.org/current/userguide/toolchains.html).
- [Spring Boot — Build systems / starters](https://docs.spring.io/spring-boot/reference/using/build-systems.html):
  Boot 4's per-technology starters (`spring-boot-starter-webmvc`,
  `spring-boot-starter-flyway`, …).
