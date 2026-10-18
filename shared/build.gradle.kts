// Generic building blocks with zero domain knowledge. Every module depends on
// this project, so anything added here is coupling every module accepts —
// keep it small.
plugins {
    id("blueprint.java-conventions")
    `java-library`
    `java-test-fixtures`
}

// The kernel must never know a module. (Its only project dependency is the
// test-fixtures wiring back to this project's own variants.)
restrictProjectDependencies(":shared must not depend on any module.") { target -> target == path }

// The throwaway test database, published as a feature of its own so :app's
// end-to-end test can use it without the module-test fixtures, whose
// ModuleTestApplication would clash with :app's own @SpringBootApplication.
val testDatabase = sourceSets.create("testDatabase")

java {
    registerFeature("testDatabase") {
        usingSourceSet(testDatabase)
    }
}

dependencies {
    api("org.springframework.data:spring-data-commons")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    // Spring's cache abstraction, and the provider chosen for this project.
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation(libs.bundles.cache)
    implementation("org.springframework:spring-tx")
    // ConstraintViolations reads Hibernate's exception type. Every -impl project
    // already has Hibernate at runtime through Spring Data JPA.
    compileOnly("org.hibernate.orm:hibernate-core")
    testImplementation("org.hibernate.orm:hibernate-core")

    // Runtime platform every module relies on: the transactional event
    // publication registry (its tables are migrated from this project),
    // Flyway and the database driver. Their settings live in
    // src/main/resources/config/blueprint-platform.yml. The registry stores
    // publications through JPA, so Spring Boot's JPA starter is here too: the
    // EntityManager must exist even in a project without modules, which
    // otherwise each bring it.
    runtimeOnly("org.springframework.modulith:spring-modulith-starter-jpa")
    runtimeOnly("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("org.springframework.boot:spring-boot-starter-flyway")
    runtimeOnly(libs.database.flyway)
    runtimeOnly(libs.database.jdbc.driver)

    // The vendor's Testcontainers module is an implementation detail of the
    // feature: consumers get it at runtime but cannot compile against it, so a
    // test cannot name the vendor and `DatabaseTestcontainers` stays the one
    // place that does.
    "testDatabaseApi"("org.springframework.boot:spring-boot-testcontainers")
    "testDatabaseImplementation"(libs.database.testcontainers)
    "testDatabaseImplementation"("org.springframework.boot:spring-boot-test")

    testFixturesApi(project(path)) {
        capabilities { requireFeature("test-database") }
    }
    testFixturesImplementation("org.springframework.boot:spring-boot-autoconfigure")
    testFixturesImplementation("org.springframework.modulith:spring-modulith-api")
}
