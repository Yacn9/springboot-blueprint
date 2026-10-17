pluginManagement {
    includeBuild("build-logic")
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "modular-monolith-blueprint"

/**
 * A business module is two Gradle projects: `<name>-api` (the contract other
 * modules compile against) and `<name>-impl` (everything else). They live in
 * `modules/<name>/api` and `modules/<name>/impl`.
 *
 * Project names stay globally unique (`users-api`, never just `api`) —
 * Gradle conflates projects that share a name and group.
 */
fun businessModule(name: String) {
    listOf("api", "impl").forEach { part ->
        include(":$name-$part")
        project(":$name-$part").projectDir = file("modules/$name/$part")
    }
}

include(":app", ":shared")

businessModule("users")
businessModule("notifications")
