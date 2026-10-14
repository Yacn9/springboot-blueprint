plugins {
    id("blueprint.module-impl")
}

dependencies {
    api(project(":notifications-api"))

    // Notifications depends on customers — never the other way round.
    implementation(project(":customers-api"))
}
