// Baseline for every Java project in the build: toolchain, BOMs, compiler
// flags and the JUnit platform. Every other convention plugin builds on this.
import java.time.Duration

plugins {
    java
}

val libs = the<VersionCatalogsExtension>().named("libs")

fun bom(alias: String) = dependencies.platform(libs.findLibrary(alias).get())

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(libs.findVersion("java").get().requiredVersion)
    }
}

// Mockito is loaded as a JVM agent at startup instead of self-attaching at
// runtime. Self-attach is deprecated on current JDKs and can deadlock a test
// JVM that already runs other threads (Testcontainers, Hikari, …).
val mockitoAgent: Configuration by configurations.creating

// Every source set gets the BOMs: test fixtures and feature variants have
// configuration hierarchies of their own that do not extend `implementation`.
sourceSets.configureEach {
    dependencies.add(implementationConfigurationName, bom("spring-boot-dependencies"))
    dependencies.add(implementationConfigurationName, bom("spring-modulith-bom"))
}

dependencies {
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    mockitoAgent(bom("spring-boot-dependencies"))
    mockitoAgent("org.mockito:mockito-core") { isTransitive = false }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // -parameters lets Spring bind @PathVariable/@RequestParam by name.
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:deprecation,unchecked"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-javaagent:${mockitoAgent.asPath}") })
    // A hung test fails the build instead of blocking CI forever.
    timeout = Duration.ofMinutes(10)
    // One image for every Testcontainers-based test (see libs.versions.toml).
    systemProperty("blueprint.database-image", libs.findVersion("database-image").get().requiredVersion)
}
