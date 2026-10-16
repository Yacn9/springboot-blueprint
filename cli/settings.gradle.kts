// A build of its own, not part of the blueprint's multi-project build: the CLI
// packs the blueprint (minus itself) as its template, and the generated
// projects must not contain it.
rootProject.name = "blueprint-cli"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
